package com.onda.marketplace.provider;

import com.onda.marketplace.auth.UserRepository;
import com.onda.marketplace.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.UUID;

@Service
public class BackgroundCheckProcessor {
    private static final Logger log = LoggerFactory.getLogger(BackgroundCheckProcessor.class);
    private final BackgroundCheckRepository checks;
    private final ProviderProfileRepository profiles;
    private final UserRepository users;
    private final BackgroundCheckGateway gateway;
    private final CpfEncryptor encryptor;
    private final NotificationService notifications;
    private final TransactionTemplate tx;
    private final TransactionTemplate outsideTx;

    BackgroundCheckProcessor(BackgroundCheckRepository checks, ProviderProfileRepository profiles,
            UserRepository users, BackgroundCheckGateway gateway, CpfEncryptor encryptor,
            NotificationService notifications, PlatformTransactionManager manager) {
        this.checks = checks;
        this.profiles = profiles;
        this.users = users;
        this.gateway = gateway;
        this.encryptor = encryptor;
        this.notifications = notifications;
        tx = new TransactionTemplate(manager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        outsideTx = new TransactionTemplate(manager);
        outsideTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
    }

    @Scheduled(fixedDelayString = "${marketplace.background-check.delay-ms:60000}",
            initialDelayString = "${marketplace.background-check.delay-ms:60000}")
    public void processPending() {
        for (UUID id : checks.pending(Instant.now(), PageRequest.of(0, 25))) {
            try { process(id); }
            catch (RuntimeException ex) {
                // Exceções do fornecedor podem conter dados pessoais: nunca imprimir payload/causa.
                log.warn("Verificação pendente; recuperação no próximo ciclo.");
            }
        }
    }

    public void process(UUID id) {
        Claim claim = tx.execute(s -> {
            BackgroundCheck job = checks.locked(id).orElse(null);
            if (job == null || !job.eligible(Instant.now())) return null;
            ProviderProfile p = profiles.findById(job.getProviderId()).orElse(null);
            if (p == null) return null; // FK normalmente elimina a tarefa junto com o perfil.
            if (!users.existsByIdAndAtivoTrue(p.getUserId()) || p.getStatusVerificacao() != ProviderStatus.EM_VERIFICACAO) {
                job.complete("CANCELADO", "CADASTRO_INDISPONIVEL", Instant.now());
                return null;
            }
            UUID token = job.claim(Instant.now());
            return new Claim(id, token, p.getUserId(), p.getId(), p.getCpfCifrado(), job.getAttempts());
        });
        if (claim == null) return;

        BackgroundCheckGateway.Result result = null;
        boolean failed = false;
        try {
            // Uma queda repetida no processamento também consome o limite de tentativas.
            if (claim.attempts() <= 3) {
                result = outsideTx.execute(s -> gateway.check(claim.id(),
                        () -> encryptor.decrypt(claim.encryptedCpf())));
                failed = result == null;
            } else failed = true;
        } catch (RuntimeException ex) { failed = true; }
        finish(claim, result, failed);
    }

    private void finish(Claim claim, BackgroundCheckGateway.Result result, boolean failed) {
        tx.executeWithoutResult(s -> {
            // Mesma ordem de travas da moderação/exclusão: usuário antes do perfil.
            var user = users.findByIdComTrava(claim.userId()).orElse(null);
            BackgroundCheck job = checks.locked(claim.id()).orElse(null);
            if (job == null || !job.owns(claim.token())) return; // resposta de lease antigo
            ProviderProfile profile = profiles.findById(claim.profileId()).orElse(null);
            if (user == null || !user.isAtivo() || profile == null
                    || profile.getStatusVerificacao() != ProviderStatus.EM_VERIFICACAO) {
                job.complete("CANCELADO", "CADASTRO_INDISPONIVEL", Instant.now());
                return;
            }
            if (failed && job.getAttempts() < 3) {
                job.retry(Instant.now());
                return;
            }
            if (failed || result == BackgroundCheckGateway.Result.INCONCLUSIVO || !profile.isCpfConciliado()) {
                job.complete("INCONCLUSIVO", failed ? "FALHA_CONSULTA" : "REVISAO_MANUAL", Instant.now());
                notifications.registrarAlerta("VERIFICACAO", profile.getId());
                return;
            }
            if (result == BackgroundCheckGateway.Result.APROVADO) profile.aprovar();
            else profile.reprovar();
            job.complete(result.name(), "CONSULTA_CONCLUIDA", Instant.now());
            if (result == BackgroundCheckGateway.Result.REPROVADO)
                notifications.registrarAlerta("VERIFICACAO", profile.getId());
        });
    }

    private record Claim(UUID id, UUID token, UUID userId, UUID profileId, String encryptedCpf, int attempts) {}
}
