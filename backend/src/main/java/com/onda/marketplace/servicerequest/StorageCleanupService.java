package com.onda.marketplace.servicerequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Collection;

@Service
public class StorageCleanupService {
    private static final Logger log = LoggerFactory.getLogger(StorageCleanupService.class);
    private final StorageDeletionRepository jobs;
    private final StorageService storage;

    StorageCleanupService(StorageDeletionRepository jobs, StorageService storage) {
        this.jobs = jobs;
        this.storage = storage;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(Collection<String> references) {
        references.stream().filter(reference -> reference.startsWith("s3://"))
                .distinct().forEach(reference -> jobs.save(new StorageDeletion(reference)));
    }

    /** DeleteObject é idempotente: uma queda após excluir no provedor permite repetir. */
    @Scheduled(fixedDelayString = "${storage.cleanup-delay-ms:60000}", initialDelayString = "${storage.cleanup-delay-ms:60000}")
    public void processPending() {
        for (StorageDeletion job : jobs.findTop50ByOrderByCreatedAtAsc()) {
            try {
                storage.delete(job.getReference());
            } catch (RuntimeException ex) {
                log.warn("Exclusão de mídia pendente; nova tentativa no próximo ciclo.");
                continue;
            }
            jobs.delete(job);
        }
    }
}
