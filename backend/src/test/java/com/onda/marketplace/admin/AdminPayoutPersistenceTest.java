package com.onda.marketplace.admin;

import com.onda.marketplace.audit.AdminAuditLog;
import com.onda.marketplace.audit.AdminAuditLogRepository;
import com.onda.marketplace.audit.AuditService;
import com.onda.marketplace.auth.User;
import com.onda.marketplace.auth.UserRepository;
import com.onda.marketplace.payment.*;
import com.onda.marketplace.proposal.ProposalRepository;
import com.onda.marketplace.provider.CpfEncryptor;
import com.onda.marketplace.provider.ProviderProfileRepository;
import com.onda.marketplace.servicerequest.*;
import com.onda.marketplace.shared.ErrorControllerAdvice;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Serviço, auditoria, repositórios financeiros e transações Spring reais, sem transação
 * envolvendo o teste. Só consultas de pedido/usuário e dependências alheias ao repasse
 * usam mocks. H2 prova rollback/trava local; o schema PostGIS continua coberto no E2E.
 */
@SpringJUnitConfig(AdminPayoutPersistenceTest.Config.class)
class AdminPayoutPersistenceTest {
    @Autowired AdminQueryService service;
    @Autowired TransactionRepository transactions;
    @Autowired OutboxEventRepository outbox;
    @Autowired AdminAuditLogRepository audit;
    @Autowired ServiceRequestRepository requests;
    @Autowired DataSource dataSource;
    private JdbcTemplate jdbc;
    private UUID txId;
    private UUID pedidoId;
    private final UUID adminId = UUID.randomUUID();

    @BeforeEach
    void preparar() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("ALTER TABLE admin_audit_log DROP CONSTRAINT IF EXISTS falha_auditoria");
        audit.deleteAll();
        outbox.deleteAll();
        transactions.deleteAll();
        reset(requests);
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();

        pedidoId = UUID.randomUUID();
        var pedido = mock(ServiceRequest.class);
        when(pedido.getStatus()).thenReturn(ServiceRequestStatus.CONCLUIDO);
        when(requests.findById(pedidoId)).thenReturn(Optional.of(pedido));
        var tx = new Transaction(pedidoId, new BigDecimal("100.00"), new BigDecimal("15.00"),
                new BigDecimal("0.15"), PaymentMethod.PIX, UUID.randomUUID().toString());
        tx.reter();
        txId = transactions.saveAndFlush(tx).getId();
        var evento = new OutboxEvent("transaction", txId, "PAYMENT_RELEASED", "{}");
        evento.marcarFalha();
        outbox.saveAndFlush(evento);
    }

    @Test
    void falhaNaAuditoria_desfazLiberacaoEFechamentoDoOutbox() {
        jdbc.execute("ALTER TABLE admin_audit_log ADD CONSTRAINT falha_auditoria "
                + "CHECK (acao <> 'CONFIRMAR_REPASSE_MANUAL')");

        assertThatThrownBy(() -> service.confirmarRepasseManual(txId, adminId, "REF-ROLLBACK"))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(transactions.findById(txId).orElseThrow().getStatusPagamento()).isEqualTo(TransactionStatus.RETIDO);
        var evento = outbox.findByAgregadoIdAndTipoEvento(txId, "PAYMENT_RELEASED").getFirst();
        assertThat(evento.getStatus()).isEqualTo(OutboxStatus.FALHA);
        assertThat(jdbc.queryForObject("SELECT processado_em FROM outbox_events WHERE id = ?",
                java.sql.Timestamp.class, evento.getId())).isNull();
        assertThat(audit.count()).isZero();
    }

    @Test
    void duasConfirmacoesReais_aSegundaEsperaOCommitENaoDuplicaAuditoria() throws Exception {
        var primeiraNaGuarda = new CountDownLatch(1);
        var liberarPrimeira = new CountDownLatch(1);
        var leituras = new AtomicInteger();
        var pedido = mock(ServiceRequest.class);
        when(pedido.getStatus()).thenReturn(ServiceRequestStatus.CONCLUIDO);
        when(requests.findById(pedidoId)).thenAnswer(invocation -> {
            if (leituras.incrementAndGet() == 1) {
                primeiraNaGuarda.countDown();
                assertThat(liberarPrimeira.await(15, TimeUnit.SECONDS)).isTrue();
            }
            return Optional.of(pedido);
        });
        var pool = Executors.newFixedThreadPool(2);
        try {
            var primeira = pool.submit(() -> service.confirmarRepasseManual(txId, adminId, "REF-PRIMEIRA"));
            assertThat(primeiraNaGuarda.await(5, TimeUnit.SECONDS)).isTrue();
            var segunda = pool.submit(() -> service.confirmarRepasseManual(txId, adminId, "REF-SEGUNDA"));
            // Observa bloqueio real no banco, sem inferir disputa por um Thread.sleep.
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                    assertThat(jdbc.queryForObject("SELECT count(*) FROM INFORMATION_SCHEMA.SESSIONS "
                            + "WHERE BLOCKER_ID IS NOT NULL", Integer.class)).isPositive());
            assertThat(segunda.isDone()).isFalse();
            liberarPrimeira.countDown();
            primeira.get(10, TimeUnit.SECONDS);
            segunda.get(10, TimeUnit.SECONDS);

            assertThat(transactions.findById(txId).orElseThrow().getStatusPagamento()).isEqualTo(TransactionStatus.LIBERADO);
            assertThat(outbox.findByAgregadoIdAndTipoEvento(txId, "PAYMENT_RELEASED"))
                    .allSatisfy(e -> assertThat(e.getStatus()).isEqualTo(OutboxStatus.PROCESSADO));
            assertThat(audit.findAll()).singleElement()
                    .satisfies(log -> {
                        assertThat(log.getAdminId()).isEqualTo(adminId);
                        assertThat(log.getEntidadeId()).isEqualTo(txId);
                        assertThat(log.getDetalhe()).isEqualTo("comprovante=REF-PRIMEIRA");
                    });
            assertThat(leituras.get()).isEqualTo(1);
        } finally {
            liberarPrimeira.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void rotaHttp_recusaCpfUuidEPedidoNaoConcluido_semAlterarBanco() throws Exception {
        var controller = new AdminController(null, null, null, service, null, null, null, null, null);
        var mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ErrorControllerAdvice()).build();
        String rota = "/api/v1/admin/transactions/" + txId + "/confirmar-repasse-manual";
        for (String referencia : List.of("11144477735", "111.444.777-35", "01234567-89AB-CDEF-0123-456789ABCDEF")) {
            mvc.perform(post(rota).contentType("application/json")
                            .content("{\"comprovanteReferencia\":\"" + referencia + "\"}"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("PAYOUT_RECEIPT_INVALID"));
        }
        var pedido = mock(ServiceRequest.class);
        when(pedido.getStatus()).thenReturn(ServiceRequestStatus.EM_ANDAMENTO);
        when(requests.findById(pedidoId)).thenReturn(Optional.of(pedido));
        mvc.perform(post(rota).contentType("application/json").content("{\"comprovanteReferencia\":\"REF-HTTP\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PAYOUT_NOT_DUE"));

        assertThat(transactions.findById(txId).orElseThrow().getStatusPagamento()).isEqualTo(TransactionStatus.RETIDO);
        assertThat(outbox.findByAgregadoIdAndTipoEvento(txId, "PAYMENT_RELEASED"))
                .allSatisfy(e -> assertThat(e.getStatus()).isEqualTo(OutboxStatus.FALHA));
        assertThat(audit.count()).isZero();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @Import(AuditService.class)
    @EnableJpaRepositories(basePackageClasses = {TransactionRepository.class, AdminAuditLogRepository.class},
            excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX,
                    pattern = "com\\.onda\\.marketplace\\.payment\\.(?!(TransactionRepository|OutboxEventRepository)$).*"))
    static class Config {
        @Bean(destroyMethod = "shutdown")
        EmbeddedDatabase dataSource() {
            return new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2)
                    .addScript("payout-test-schema.sql").build();
        }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            // Pedido/usuário entram no metamodelo para validar JPQL dos repositórios reais.
            // Suas tabelas geográficas não são usadas neste teste financeiro focado.
            factory.setManagedTypes(PersistenceManagedTypes.of(Transaction.class.getName(), OutboxEvent.class.getName(),
                    AdminAuditLog.class.getName(), ServiceRequest.class.getName(), User.class.getName()));
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "none"));
            return factory;
        }

        @Bean
        JpaTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }

        @Bean ServiceRequestRepository requests() { return mock(ServiceRequestRepository.class); }
        @Bean UserRepository users() { return mock(UserRepository.class); }

        @Bean
        AdminQueryService service(ServiceRequestRepository requests, TransactionRepository transactions,
                                  OutboxEventRepository outbox, UserRepository users, AuditService audit) {
            return new AdminQueryService(requests, transactions, outbox, mock(DisputeResolutionRepository.class),
                    mock(ProposalRepository.class), mock(ServiceMediaRepository.class), users,
                    mock(ProviderProfileRepository.class), mock(CpfEncryptor.class), audit, mock(com.onda.marketplace.servicerequest.StorageService.class));
        }
    }
}
