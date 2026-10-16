package com.onda.marketplace.servicerequest;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class StorageCleanupServiceTest {
    final StorageDeletionRepository jobs = mock(StorageDeletionRepository.class);
    final StorageService storage = mock(StorageService.class);
    final StorageCleanupService cleanup = new StorageCleanupService(jobs, storage);

    @Test
    void enqueuesOnlyRealObjects() {
        cleanup.enqueue(List.of("https://legacy.example.org/photo", "s3://bucket/key"));
        verify(jobs).save(argThat(job -> job.getReference().equals("s3://bucket/key")));
        verifyNoInteractions(storage);
    }

    @Test
    void providerOutageKeepsJobForRetry() {
        var job = new StorageDeletion("s3://bucket/key");
        when(jobs.findTop50ByOrderByCreatedAtAsc()).thenReturn(List.of(job));
        doThrow(new StorageUnavailableException()).when(storage).delete(job.getReference());
        cleanup.processPending();
        verify(jobs, never()).delete(any(StorageDeletion.class));
    }

    @Test
    void successfulDeletionAcknowledgesJobAfterProvider() {
        var job = new StorageDeletion("s3://bucket/key");
        when(jobs.findTop50ByOrderByCreatedAtAsc()).thenReturn(List.of(job));
        cleanup.processPending();
        var order = inOrder(storage, jobs);
        order.verify(storage).delete(job.getReference());
        order.verify(jobs).delete(job);
    }
}
