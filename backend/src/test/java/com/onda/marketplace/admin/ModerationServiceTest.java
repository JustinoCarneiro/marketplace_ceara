package com.onda.marketplace.admin;

import com.onda.marketplace.auth.User;
import com.onda.marketplace.auth.UserRepository;
import com.onda.marketplace.auth.UserRole;
import com.onda.marketplace.notification.NotificationService;
import com.onda.marketplace.provider.ProviderProfile;
import com.onda.marketplace.provider.ProviderProfileRepository;
import com.onda.marketplace.provider.ProviderProfiles;
import com.onda.marketplace.provider.ProviderStatus;
import com.onda.marketplace.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class ModerationServiceTest {

    @Mock ProviderProfileRepository providerProfileRepository;
    @Mock UserRepository            userRepository;
    @Mock NotificationService       notificationService;

    ModerationService service;

    private static final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ModerationService(providerProfileRepository, userRepository, notificationService);
    }

    private static User dono() {
        return User.builder().nome("Prestador").email("p@x.com").senhaHash("$2a$x").role(UserRole.ROLE_PROVIDER).build();
    }

    /** O usuário travado (PRIMEIRA leitura da moderação) e vivo — a exclusão de conta toma a mesma trava. */
    private void donoVivo() {
        when(userRepository.findByIdComTrava(USER_ID)).thenReturn(Optional.of(dono()));
    }

    @Test
    void moderar_aprovar_chamaAprovar() {
        var profile = mock(ProviderProfile.class);
        donoVivo();
        when(providerProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(profile));
        when(providerProfileRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.moderar(USER_ID, ModerationAction.APROVAR);

        verify(profile).aprovar();
        verify(providerProfileRepository).save(profile);
    }

    @Test
    void moderar_reprovar_chamaReprovar_e_criaAlertaVerificacao() {
        var profile = mock(ProviderProfile.class);
        donoVivo();
        when(providerProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(profile));
        when(providerProfileRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(notificationService.criarAlerta(any(), any())).thenReturn(null);

        service.moderar(USER_ID, ModerationAction.REPROVAR);

        verify(profile).reprovar();
        verify(notificationService).criarAlerta("VERIFICACAO", profile.getId());
    }

    @Test
    void moderar_suspender_chamaSuspender() {
        var profile = mock(ProviderProfile.class);
        donoVivo();
        when(providerProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(profile));
        when(providerProfileRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.moderar(USER_ID, ModerationAction.SUSPENDER);

        verify(profile).suspender();
    }

    @Test
    void moderar_travaOUsuarioAntesDeLerOPerfil_aTravaEAPrimeiraLeitura() {
        // Revisão cruzada (2ª rodada): é a trava da exclusão de conta, que anonimiza o perfil. Ordem importa — sobre uma
        // entidade já carregada o Hibernate devolve a instância antiga, então a trava tem de vir antes de qualquer leitura.
        var profile = mock(ProviderProfile.class);
        donoVivo();
        when(providerProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(profile));
        when(providerProfileRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.moderar(USER_ID, ModerationAction.APROVAR);

        var ordem = inOrder(userRepository, providerProfileRepository);
        ordem.verify(userRepository).findByIdComTrava(USER_ID);
        ordem.verify(providerProfileRepository).findByUserId(USER_ID);
        verify(userRepository, never()).findById(any());
    }

    @Test
    void moderar_prestadorComContaExcluida_recusa_eNaoMexeNoStatus() {
        // aprovar um perfil excluído o faria aparecer VERIFICADO na lista do admin, ao lado de "Usuário removido"
        var excluido = dono();
        excluido.anonimizar("removido-1@excluido.invalid", "hash-inutilizavel", false);
        when(userRepository.findByIdComTrava(USER_ID)).thenReturn(Optional.of(excluido));
        var profile = ProviderProfiles.comStatus(ProviderStatus.SUSPENSO);

        for (ModerationAction acao : ModerationAction.values()) {
            assertThatThrownBy(() -> service.moderar(USER_ID, acao))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", "ACCOUNT_DELETED");
        }

        assertThat(profile.getStatusVerificacao()).isEqualTo(ProviderStatus.SUSPENSO);
        verifyNoInteractions(providerProfileRepository, notificationService);
    }

    @Test
    void moderar_prestadorNaoEncontrado_lancaException() {
        donoVivo();
        when(providerProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.moderar(USER_ID, ModerationAction.APROVAR))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PROVIDER_NOT_FOUND");
    }

    @Test
    void moderar_usuarioInexistente_lancaProviderNotFound() {
        when(userRepository.findByIdComTrava(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.moderar(USER_ID, ModerationAction.APROVAR))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PROVIDER_NOT_FOUND");
        verifyNoInteractions(providerProfileRepository);
    }
}
