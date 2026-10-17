package com.onda.marketplace.e2e;

import com.onda.marketplace.admin.AdminQueryService;
import com.onda.marketplace.notification.NotificationService;
import com.onda.marketplace.payment.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Dois workers independentes usam o mesmo banco e os proxies transacionais reais. */
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers
@ActiveProfiles("e2e")
@SpringBootTest(properties = "marketplace.outbox.delay-ms=3600000")
class E2EOutboxConcurrencyTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:15-3.4").asCompatibleSubstituteFor("postgres"));
    @DynamicPropertySource
    static void db(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", postgres::getJdbcUrl);
        p.add("spring.datasource.username", postgres::getUsername);
        p.add("spring.datasource.password", postgres::getPassword);
    }
    @Autowired OutboxEventRepository events;
    @Autowired TransactionRepository transactions;
    @Autowired OutboxEventWriter writer;
    @Autowired JdbcTemplate jdbc;
    @Autowired AdminQueryService admin;
    @Autowired PlatformTransactionManager manager;
    @Autowired com.onda.marketplace.payment.PaymentService payments;
    UUID adminId;

    @BeforeEach void clear() {
        events.deleteAll();
        transactions.deleteAll();
        adminId = UUID.randomUUID();
        jdbc.update("insert into users(id,nome,email,senha_hash,role) values (?,'Teste',?,'hash','ROLE_ADMIN')",
                adminId, adminId + "@example.test");
    }
    Transaction tx(boolean retained) {
        var request = UUID.randomUUID();
        jdbc.update("insert into service_requests(id,cliente_id,categoria,status,idempotency_key) values (?,?,'Teste','CONCLUIDO',?)",
                request, adminId, request.toString());
        var tx = new Transaction(request, BigDecimal.TEN, BigDecimal.ONE, new BigDecimal("0.10"), PaymentMethod.PIX, request.toString());
        if (retained) tx.reter();
        return transactions.saveAndFlush(tx);
    }
    OutboxEvent event(Transaction tx, String type) {
        return events.saveAndFlush(new OutboxEvent("transaction", tx.getId(), type, "{}"));
    }
    OutboxProcessor worker(GatewayService gateway) {
        return new OutboxProcessor(transactions, gateway, mock(NotificationService.class), writer);
    }
    void outsideTransaction() { assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse(); }
    static void await(CountDownLatch latch) {
        try { assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue(); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new AssertionError(ex); }
    }

    @Test void doisWorkers_naoChamamGatewayParaOMesmoEvento() throws Exception {
        var tx = tx(false); var e = event(tx, "PAYMENT_INITIATED");
        var entered = new CountDownLatch(1); var finish = new CountDownLatch(1);
        var gateway = mock(GatewayService.class);
        when(gateway.cobrar(any())).thenAnswer(inv -> { outsideTransaction(); entered.countDown(); await(finish); return "gw-one"; });
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> worker(gateway).processOutbox());
            try {
                await(entered);
                pool.submit(() -> worker(gateway).processOutbox()).get(5, TimeUnit.SECONDS);
                assertThat(events.findById(e.getId()).orElseThrow().getStatus()).isEqualTo(OutboxStatus.PROCESSANDO);
                verify(gateway, times(1)).cobrar(any());
            } finally { finish.countDown(); }
            first.get(10, TimeUnit.SECONDS);
        }
        assertThat(events.findById(e.getId()).orElseThrow().getStatus()).isEqualTo(OutboxStatus.PROCESSADO);
        assertThat(transactions.findById(tx.getId()).orElseThrow().getGatewayTransactionId()).isEqualTo("gw-one");
    }

    @Test void mesmaTransacao_naoAdmiteOutroEventoNemIntervencaoAdminDuranteGateway() throws Exception {
        var tx = tx(true); var release = event(tx, "PAYMENT_RELEASED");
        jdbc.update("update outbox_events set criado_em=now()-interval '1 minute' where id=?", release.getId());
        var refund = event(tx, "PAYMENT_REFUNDED");
        var entered = new CountDownLatch(1); var finish = new CountDownLatch(1);
        var gateway = mock(GatewayService.class);
        doAnswer(inv -> { outsideTransaction(); entered.countDown(); await(finish); return null; }).when(gateway).liberar(any());
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> worker(gateway).processOutbox());
            try {
                await(entered);
                pool.submit(() -> worker(gateway).processOutbox()).get(5, TimeUnit.SECONDS);
                assertThatThrownBy(() -> admin.reprocessarOutbox(refund.getId())).hasFieldOrPropertyWithValue("code", "OUTBOX_IN_PROGRESS");
                assertThatThrownBy(() -> admin.confirmarRepasseManual(tx.getId(), adminId, "REF-TESTE")).hasFieldOrPropertyWithValue("code", "OUTBOX_IN_PROGRESS");
                verify(gateway, never()).reembolsar(any());
            } finally { finish.countDown(); }
            first.get(10, TimeUnit.SECONDS);
        }
        assertThat(transactions.findById(tx.getId()).orElseThrow().getStatusPagamento()).isEqualTo(TransactionStatus.LIBERADO);
        assertThat(events.findById(refund.getId()).orElseThrow().getStatus()).isEqualTo(OutboxStatus.FALHA);
        verify(gateway, never()).reembolsar(any());
    }

    @Test void skipLocked_processaOutraTransacaoEnquantoPrimeiraEstaTravada() throws Exception {
        var first = tx(false); var second = tx(false);
        var blocked = event(first, "PAYMENT_INITIATED");
        jdbc.update("update outbox_events set criado_em=now()-interval '1 minute' where id=?", blocked.getId());
        var available = event(second, "PAYMENT_INITIATED");
        var locked = new CountDownLatch(1); var finish = new CountDownLatch(1);
        var gateway = mock(GatewayService.class);
        when(gateway.cobrar(any())).thenAnswer(inv -> { outsideTransaction(); return "gw-second"; });
        try (var pool = Executors.newFixedThreadPool(2)) {
            var owner = pool.submit(() -> new TransactionTemplate(manager).executeWithoutResult(s -> {
                transactions.findByIdComTrava(first.getId()).orElseThrow(); locked.countDown(); await(finish);
            }));
            try {
                await(locked);
                pool.submit(() -> worker(gateway).processOutbox()).get(5, TimeUnit.SECONDS);
                assertThat(events.findById(available.getId()).orElseThrow().getStatus()).isEqualTo(OutboxStatus.PROCESSADO);
                assertThat(events.findById(blocked.getId()).orElseThrow().getStatus()).isEqualTo(OutboxStatus.PENDENTE);
                verify(gateway, times(1)).cobrar(any());
            } finally { finish.countDown(); }
            owner.get(10, TimeUnit.SECONDS);
        }
    }

    @Test void claimAntigo_naoGravaResultadoNemFalha() {
        var tx = tx(false); var e = event(tx, "PAYMENT_INITIATED");
        var old = writer.reservarProximo();
        jdbc.update("update outbox_events set claim_token=? where id=?", UUID.randomUUID(), e.getId());
        tx.setGatewayTransactionId("obsolete");
        writer.salvarResultadoPagamento(tx, old, true);
        writer.salvarResultadoPagamento(tx, old, false);
        assertThat(transactions.findById(tx.getId()).orElseThrow().getGatewayTransactionId()).isNull();
        assertThat(events.findById(e.getId()).orElseThrow().getStatus()).isEqualTo(OutboxStatus.PROCESSANDO);
    }

    @Test void erroAoPersistirAposGateway_mantemClaimParaReconciliacao() {
        var tx = tx(false); var e = event(tx, "PAYMENT_INITIATED");
        var gateway = mock(GatewayService.class);
        when(gateway.cobrar(any())).thenAnswer(inv -> { outsideTransaction(); return "gw-external-success"; });
        jdbc.execute("alter table outbox_events add constraint impedir_conclusao check(status<>'PROCESSADO') not valid");
        try {
            worker(gateway).processOutbox();
            worker(gateway).processOutbox();
            verify(gateway, times(1)).cobrar(any());
            assertThat(events.findById(e.getId()).orElseThrow().getStatus()).isEqualTo(OutboxStatus.PROCESSANDO);
            assertThat(transactions.findById(tx.getId()).orElseThrow().getGatewayTransactionId()).isNull();
        } finally { jdbc.execute("alter table outbox_events drop constraint impedir_conclusao"); }
    }

    @Test void resultadoDaCobranca_naoRegrideWebhookConcorrente() {
        var tx = tx(false); tx.setGatewayTransactionId("gw-known"); transactions.saveAndFlush(tx);
        var e = event(tx, "PAYMENT_INITIATED"); var claim = writer.reservarProximo();
        var snapshot = transactions.findById(tx.getId()).orElseThrow();
        payments.confirmPayment("gw-known", "PAGO");
        writer.salvarResultadoPagamento(snapshot, claim, true);
        assertThat(transactions.findById(tx.getId()).orElseThrow().getStatusPagamento()).isEqualTo(TransactionStatus.RETIDO);
        assertThat(events.findById(e.getId()).orElseThrow().getStatus()).isEqualTo(OutboxStatus.PROCESSADO);
    }

    @Test void falhaDoGateway_podeSerReenfileiradaSemPerderIdempotencia() {
        var tx = tx(false); var e = event(tx, "PAYMENT_INITIATED");
        var calls = new AtomicInteger(); var gateway = mock(GatewayService.class);
        when(gateway.cobrar(any())).thenAnswer(inv -> {
            outsideTransaction(); assertThat(((Transaction)inv.getArgument(0)).getIdempotencyKey()).isEqualTo(tx.getIdempotencyKey());
            if (calls.incrementAndGet() == 1) throw new IllegalStateException("provider unavailable");
            return "gw-retry";
        });
        worker(gateway).processOutbox();
        assertThat(events.findById(e.getId()).orElseThrow().getStatus()).isEqualTo(OutboxStatus.FALHA);
        admin.reprocessarOutbox(e.getId());
        worker(gateway).processOutbox();
        assertThat(events.findById(e.getId()).orElseThrow().getStatus()).isEqualTo(OutboxStatus.PROCESSADO);
        assertThat(calls).hasValue(2);
    }

    @Test void claimAbandonado_naoExpiraNemPermiteReenvioCego() {
        var tx = tx(false); var e = event(tx, "PAYMENT_INITIATED");
        writer.reservarProximo();
        jdbc.update("update outbox_events set processamento_em=now()-interval '1 day' where id=?", e.getId());
        assertThat(writer.reservarProximo()).isNull();
        assertThatThrownBy(() -> admin.reprocessarOutbox(e.getId())).hasFieldOrPropertyWithValue("code", "OUTBOX_IN_PROGRESS");
        assertThat(admin.findOutbox(OutboxStatus.PROCESSANDO)).hasSize(1).first().hasFieldOrProperty("processamentoEm");
    }
    @Test void sosInterrompido_retomadoAposPrazo_semAceitarConclusaoAntiga() {
        var e = events.saveAndFlush(new OutboxEvent("sos_alert", UUID.randomUUID(), "SOS_TRIGGERED", "{}"));
        var old = writer.reservarProximo();
        assertThat(writer.reservarProximo()).isNull();
        jdbc.update("update outbox_events set processamento_em=now()-interval '6 minutes' where id=?", e.getId());
        var current = writer.reservarProximo();
        assertThat(current.getId()).isEqualTo(e.getId());
        assertThat(current.getClaimToken()).isNotEqualTo(old.getClaimToken());
        writer.marcarProcessado(old);
        assertThat(events.findById(e.getId()).orElseThrow().getStatus()).isEqualTo(OutboxStatus.PROCESSANDO);
        writer.marcarProcessado(current);
        assertThat(events.findById(e.getId()).orElseThrow().getStatus()).isEqualTo(OutboxStatus.PROCESSADO);
    }

    @Test void sosTemPrioridadeSobreFilaFinanceiraContinua() {
        var tx = tx(false); event(tx, "PAYMENT_INITIATED");
        var sos = events.saveAndFlush(new OutboxEvent("sos_alert", UUID.randomUUID(), "SOS_TRIGGERED", "{}"));
        assertThat(writer.reservarProximo().getId()).isEqualTo(sos.getId());
    }

}
