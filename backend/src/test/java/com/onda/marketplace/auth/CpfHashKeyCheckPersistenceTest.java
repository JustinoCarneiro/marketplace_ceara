package com.onda.marketplace.auth;

import com.onda.marketplace.provider.CpfEncryptor;
import com.onda.marketplace.provider.ProviderProfileRepository;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** JPA e H2 reais, sem transação do teste que possa esconder uma gravação parcial. */
@SpringJUnitConfig(CpfHashKeyCheckPersistenceTest.Config.class)
class CpfHashKeyCheckPersistenceTest {

    @Autowired CpfHashKeyVerificacaoRepository repository;
    @Autowired DataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeEach
    void limpar() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("ALTER TABLE cpf_hash_key_verificacoes DROP CONSTRAINT IF EXISTS falha_anterior");
        repository.deleteAll();
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }

    @Test
    void falhaAoGravarAnterior_desfazTambemAVersaoAtual() {
        // Só a segunda versão é rejeitada pelo banco; a atual, sozinha, seria válida.
        jdbc.execute("ALTER TABLE cpf_hash_key_verificacoes ADD CONSTRAINT falha_anterior CHECK (versao <> 1)");

        assertThatThrownBy(check()::verificar).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM cpf_hash_key_verificacoes", Integer.class)).isZero();
    }

    @Test
    void confirmacaoDoOperador_persisteAsDuasVersoes() {
        check().verificar();

        var hash = new CpfHashService(CpfHashKeyCheckTest.ATUAL, 2, CpfHashKeyCheckTest.ANTERIOR);
        assertThat(repository.findAll()).hasSize(2);
        assertThat(repository.findById(2).orElseThrow().getVerificador()).isEqualTo(hash.verificadorChaveAtual());
        assertThat(repository.findById(1).orElseThrow().getVerificador()).isEqualTo(hash.verificadorChaveAnterior());
    }

    private CpfHashKeyCheck check() {
        var users = mock(UserRepository.class);
        when(users.countByCpfHashIsNotNullAndCpfHashVersao(1)).thenReturn(3L);
        return new CpfHashKeyCheck(users,
                new CpfHashService(CpfHashKeyCheckTest.ATUAL, 2, CpfHashKeyCheckTest.ANTERIOR),
                mock(ProviderProfileRepository.class), mock(CpfEncryptor.class), repository, false, true);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableJpaRepositories(basePackageClasses = CpfHashKeyVerificacaoRepository.class,
            excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX,
                    pattern = "com\\.onda\\.marketplace\\.auth\\.(?!CpfHashKeyVerificacaoRepository$).*"))
    static class Config {
        @Bean(destroyMethod = "shutdown")
        org.springframework.jdbc.datasource.embedded.EmbeddedDatabase dataSource() {
            return new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setManagedTypes(PersistenceManagedTypes.of(CpfHashKeyVerificacao.class.getName()));
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop"));
            return factory;
        }

        @Bean
        JpaTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }
    }
}
