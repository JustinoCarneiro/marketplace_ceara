package com.onda.marketplace.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica que Virtual Threads estão habilitados (TS01).
 * Spring Boot 3.2+ usa VirtualThreadTaskExecutor quando
 * spring.threads.virtual.enabled=true.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class VirtualThreadsTest {

    @Autowired
    AsyncTaskExecutor applicationTaskExecutor;

    @Test
    void applicationTaskExecutor_usesVirtualThreads() throws Exception {
        AtomicBoolean isVirtual = new AtomicBoolean(false);

        applicationTaskExecutor.submit(() ->
                isVirtual.set(Thread.currentThread().isVirtual())
        ).get();

        assertThat(isVirtual.get())
                .as("spring.threads.virtual.enabled deve ativar Virtual Threads")
                .isTrue();
    }
}
