package com.onda.marketplace.admin;

import com.onda.marketplace.notification.NotificationService;
import com.onda.marketplace.provider.ProviderProfile;
import com.onda.marketplace.provider.ProviderProfileRepository;
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
    @Mock NotificationService       notificationService;

    ModerationService service;

    private static final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ModerationService(providerProfileRepository, notificationService);
    }

    @Test
    void moderar_aprovar_chamaAprovar() {
        var profile = mock(ProviderProfile.class);
        when(providerProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(profile));
        when(providerProfileRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.moderar(USER_ID, ModerationAction.APROVAR);

        verify(profile).aprovar();
        verify(providerProfileRepository).save(profile);
    }

    @Test
    void moderar_reprovar_chamaReprovar_e_criaAlertaVerificacao() {
        var profile = mock(ProviderProfile.class);
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
        when(providerProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(profile));
        when(providerProfileRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.moderar(USER_ID, ModerationAction.SUSPENDER);

        verify(profile).suspender();
    }

    @Test
    void moderar_prestadorNaoEncontrado_lancaException() {
        when(providerProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.moderar(USER_ID, ModerationAction.APROVAR))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PROVIDER_NOT_FOUND");
    }
}
