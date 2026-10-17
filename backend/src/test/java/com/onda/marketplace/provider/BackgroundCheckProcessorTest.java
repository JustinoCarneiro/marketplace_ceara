package com.onda.marketplace.provider;

import com.onda.marketplace.auth.*;
import com.onda.marketplace.notification.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BackgroundCheckProcessorTest {
    @Mock BackgroundCheckRepository jobs;
    @Mock ProviderProfileRepository profiles;
    @Mock UserRepository users;
    @Mock BackgroundCheckGateway gateway;
    @Mock NotificationService notifications;
    @Mock PlatformTransactionManager manager;
    final CpfEncryptor encryptor = new CpfEncryptor("01234567890123456789012345678901");
    BackgroundCheckProcessor processor;
    BackgroundCheck job;
    ProviderProfile profile;
    User user;

    @BeforeEach void setup() {
        processor = new BackgroundCheckProcessor(jobs, profiles, users, gateway, encryptor, notifications, manager);
        user = User.builder().nome("Teste").email("fixture@example.test").senhaHash("hash").role(UserRole.ROLE_PROVIDER).build();
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        profile = new ProviderProfile(user, "PINTOR", encryptor.encrypt("11144477735"));
        ReflectionTestUtils.setField(profile, "id", UUID.randomUUID());
        job = new BackgroundCheck(profile.getId());
        ReflectionTestUtils.setField(job, "id", UUID.randomUUID());
    }
    void available() {
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(jobs.locked(job.getId())).thenReturn(Optional.of(job));
        when(profiles.findById(profile.getId())).thenReturn(Optional.of(profile));
    }
    void finishable() {
        available();
        when(users.findByIdComTrava(user.getId())).thenReturn(Optional.of(user));
    }
    @Test void approvalRequiresDocumentedGatewayResult() {
        finishable();
        when(gateway.check(eq(job.getId()), any())).thenAnswer(i -> {
            @SuppressWarnings("unchecked") var cpf = (java.util.function.Supplier<String>) i.getArgument(1);
            assertThat(cpf.get()).isEqualTo("11144477735");
            // Claim has committed before contacting the gateway.
            verify(manager).commit(any());
            return BackgroundCheckGateway.Result.APROVADO;
        });
        processor.process(job.getId());
        assertThat(profile.getStatusVerificacao()).isEqualTo(ProviderStatus.VERIFICADO);
        assertThat(job.getStatus()).isEqualTo("APROVADO");
        verifyNoInteractions(notifications);
    }
    @Test void manualFallbackDoesNotDecryptOrApprove() {
        finishable();
        ReflectionTestUtils.setField(profile, "cpfCifrado", "not-a-valid-ciphertext");
        var fallback = new ManualBackgroundCheckGateway().manualBackgroundCheckGateway();
        when(gateway.check(any(), any())).thenAnswer(i -> fallback.check(i.getArgument(0), i.getArgument(1)));
        processor.process(job.getId());
        assertThat(profile.getStatusVerificacao()).isEqualTo(ProviderStatus.EM_VERIFICACAO);
        assertThat(job.getStatus()).isEqualTo("INCONCLUSIVO");
        verify(notifications).registrarAlerta("VERIFICACAO", profile.getId());
        processor.process(job.getId());
        verify(gateway, times(1)).check(any(), any());
        verify(notifications, times(1)).registrarAlerta(any(), any());
    }
    @Test void rejectionIsPersistedAndNotifiesAdmin() {
        finishable();
        when(gateway.check(any(), any())).thenReturn(BackgroundCheckGateway.Result.REPROVADO);
        processor.process(job.getId());
        assertThat(profile.getStatusVerificacao()).isEqualTo(ProviderStatus.REPROVADO);
        assertThat(job.getStatus()).isEqualTo("REPROVADO");
        verify(notifications).registrarAlerta("VERIFICACAO", profile.getId());
    }
    @Test void retryIsBoundedAndEndsInManualReview() {
        finishable();
        when(gateway.check(any(), any())).thenThrow(new IllegalStateException("untrusted sensitive response"));
        for (int i = 0; i < 3; i++) {
            ReflectionTestUtils.setField(job, "availableAt", Instant.EPOCH);
            processor.process(job.getId());
        }
        assertThat(job.getAttempts()).isEqualTo(3);
        assertThat(job.getStatus()).isEqualTo("INCONCLUSIVO");
        assertThat(job.getResultado()).isEqualTo("FALHA_CONSULTA");
        assertThat(profile.getStatusVerificacao()).isEqualTo(ProviderStatus.EM_VERIFICACAO);
        verify(notifications, times(1)).registrarAlerta(any(), any());
    }
    @Test void nullResultFailsClosed() {
        finishable();
        processor.process(job.getId());
        assertThat(job.getStatus()).isEqualTo("PENDENTE");
        verifyNoInteractions(notifications);
    }
    @Test void activeLeasePreventsDuplicateDelivery() {
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(jobs.locked(job.getId())).thenReturn(Optional.of(job));
        job.claim(Instant.now());
        processor.process(job.getId());
        verifyNoInteractions(gateway, profiles, users);
    }
    @Test void expiredLeaseIsRecovered() {
        finishable();
        job.claim(Instant.now().minusSeconds(200));
        when(gateway.check(any(), any())).thenReturn(BackgroundCheckGateway.Result.INCONCLUSIVO);
        processor.process(job.getId());
        assertThat(job.getAttempts()).isEqualTo(2);
        assertThat(job.getStatus()).isEqualTo("INCONCLUSIVO");
    }
    @Test void lateApprovalCannotUndoModeration() {
        finishable();
        when(gateway.check(any(), any())).thenAnswer(i -> { profile.suspender(); return BackgroundCheckGateway.Result.APROVADO; });
        processor.process(job.getId());
        assertThat(profile.getStatusVerificacao()).isEqualTo(ProviderStatus.SUSPENSO);
        assertThat(job.getStatus()).isEqualTo("CANCELADO");
    }
    @Test void lateApprovalCannotUndoAccountDeletion() {
        finishable();
        when(gateway.check(any(), any())).thenAnswer(i -> {
            user.anonimizar("removed@example.test", "disabled", false);
            return BackgroundCheckGateway.Result.APROVADO;
        });
        processor.process(job.getId());
        assertThat(job.getStatus()).isEqualTo("CANCELADO");
        assertThat(profile.getStatusVerificacao()).isEqualTo(ProviderStatus.EM_VERIFICACAO);
    }
    @Test void deletedAccountDoesNotSendCpfToGateway() {
        available();
        user.anonimizar("removed@example.test", "disabled", false);
        processor.process(job.getId());
        assertThat(job.getStatus()).isEqualTo("CANCELADO");
        verifyNoInteractions(gateway, users);
    }
    @Test void duplicateCpfCannotBeApprovedAutomatically() {
        finishable();
        profile.marcarCpfNaoConciliado();
        when(gateway.check(any(), any())).thenReturn(BackgroundCheckGateway.Result.APROVADO);
        processor.process(job.getId());
        assertThat(job.getStatus()).isEqualTo("INCONCLUSIVO");
        assertThat(profile.getStatusVerificacao()).isEqualTo(ProviderStatus.EM_VERIFICACAO);
    }
    @Test void resultFromOldLeaseCannotOverrideNewOwner() {
        finishable();
        when(gateway.check(any(), any())).thenAnswer(i -> { job.claim(Instant.now()); return BackgroundCheckGateway.Result.APROVADO; });
        processor.process(job.getId());
        assertThat(job.getStatus()).isEqualTo("PROCESSANDO");
        assertThat(profile.getStatusVerificacao()).isEqualTo(ProviderStatus.EM_VERIFICACAO);
        verifyNoInteractions(notifications);
    }
    @Test void repeatedCrashesConsumeAttemptLimitWithoutAnotherCall() {
        finishable();
        ReflectionTestUtils.setField(job, "attempts", 3);
        processor.process(job.getId());
        assertThat(job.getStatus()).isEqualTo("INCONCLUSIVO");
        verifyNoInteractions(gateway);
    }
}
