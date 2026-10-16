package com.onda.marketplace.message;

import com.onda.marketplace.auth.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.*;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.*;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(ChatCommitTest.Config.class)
class ChatCommitTest {
    @Autowired ApplicationEventPublisher events;
    @Autowired AsyncTaskExecutor executor;
    @Autowired DataSourceTransactionManager manager;

    @Test void rollbackDoesNotDispatchAndCommitDispatchesOnlyAfterTransaction() {
        reset(executor);
        var event = new MessageCommitted(UUID.randomUUID(), UUID.randomUUID());
        assertThatThrownBy(() -> new TransactionTemplate(manager).executeWithoutResult(status -> {
            events.publishEvent(event);
            verifyNoInteractions(executor);
            throw new IllegalStateException("rollback esperado");
        })).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(executor);
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            events.publishEvent(event);
            verifyNoInteractions(executor);
        });
        verify(executor).execute(any(Runnable.class));
    }

    @Test void rejectedNotificationDoesNotFailAlreadyCommittedMessage() {
        reset(executor);
        doThrow(new org.springframework.core.task.TaskRejectedException("executor encerrado"))
                .when(executor).execute(any(Runnable.class));
        try {
            assertThatCode(() -> new TransactionTemplate(manager).executeWithoutResult(status ->
                    events.publishEvent(new MessageCommitted(UUID.randomUUID(), UUID.randomUUID()))))
                    .doesNotThrowAnyException();
        } finally { reset(executor); }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @Import(ChatStreamService.class)
    static class Config {
        @Bean(destroyMethod="shutdown") EmbeddedDatabase dataSource() {
            return new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        }
        @Bean DataSourceTransactionManager transactionManager(EmbeddedDatabase source) { return new DataSourceTransactionManager(source); }
        @Bean(name="applicationTaskExecutor") AsyncTaskExecutor executor() { return mock(AsyncTaskExecutor.class); }
        @Bean ChatAccessService access() { return mock(ChatAccessService.class); }
        @Bean MessageRepository messages() { return mock(MessageRepository.class); }
        @Bean UserRepository users() { return mock(UserRepository.class); }
    }
}
