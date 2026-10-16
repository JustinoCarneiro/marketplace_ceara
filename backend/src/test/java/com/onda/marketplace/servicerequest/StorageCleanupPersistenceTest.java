package com.onda.marketplace.servicerequest;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.embedded.*;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(StorageCleanupPersistenceTest.Config.class)
class StorageCleanupPersistenceTest {
    @Autowired StorageCleanupService cleanup;
    @Autowired StorageDeletionRepository jobs;
    @Autowired StorageService storage;
    @Autowired JpaTransactionManager manager;

    @BeforeEach
    void resetState() { jobs.deleteAll(); reset(storage); }

    @Test
    void rolledBackAccountDeletionDoesNotLeaveDeletionJob() {
        assertThatThrownBy(() -> new TransactionTemplate(manager).executeWithoutResult(status -> {
            cleanup.enqueue(List.of("s3://private-media/key"));
            throw new IllegalStateException("simulated account failure");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jobs.count()).isZero();
        verifyNoInteractions(storage);
    }

    @Test
    void committedJobSurvivesProviderFailureUntilSuccessfulRetry() {
        new TransactionTemplate(manager).executeWithoutResult(status -> cleanup.enqueue(List.of("s3://private-media/key")));
        verifyNoInteractions(storage);
        doThrow(new StorageUnavailableException()).doNothing().when(storage).delete("s3://private-media/key");
        cleanup.processPending();
        assertThat(jobs.count()).isOne();
        cleanup.processPending();
        assertThat(jobs.count()).isZero();
        verify(storage, times(2)).delete("s3://private-media/key");
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @Import(StorageCleanupService.class)
    @EnableJpaRepositories(basePackageClasses = StorageDeletionRepository.class,
            excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX,
                    pattern = "com\\.onda\\.marketplace\\.servicerequest\\.(?!StorageDeletionRepository$).*"))
    static class Config {
        @Bean(destroyMethod = "shutdown") EmbeddedDatabase dataSource() {
            return new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(source);
            factory.setManagedTypes(PersistenceManagedTypes.of(StorageDeletion.class.getName()));
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop"));
            return factory;
        }
        @Bean JpaTransactionManager transactionManager(EntityManagerFactory factory) { return new JpaTransactionManager(factory); }
        @Bean StorageService storage() { return mock(StorageService.class); }
    }
}
