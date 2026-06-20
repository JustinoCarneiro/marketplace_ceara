package com.onda.marketplace.admin;

import com.onda.marketplace.notification.NotificationService;
import com.onda.marketplace.provider.ProviderProfile;
import com.onda.marketplace.provider.ProviderProfileRepository;
import com.onda.marketplace.shared.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Moderação de prestadores (US26): aprovar/reprovar a verificação ou suspender
 * um prestador já ativo. Atua sobre o {@link ProviderProfile} identificado pelo
 * userId.
 */
@Service
@SuppressWarnings("null")
public class ModerationService {

    private final ProviderProfileRepository providerProfileRepository;
    private final NotificationService       notificationService;

    public ModerationService(ProviderProfileRepository providerProfileRepository,
                             NotificationService notificationService) {
        this.providerProfileRepository = providerProfileRepository;
        this.notificationService       = notificationService;
    }

    @Transactional
    public void moderar(UUID userId, ModerationAction action) {
        ProviderProfile profile = providerProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException("PROVIDER_NOT_FOUND",
                        "Prestador não encontrado."));

        switch (action) {
            case APROVAR   -> profile.aprovar();
            case REPROVAR  -> profile.reprovar();
            case SUSPENDER -> profile.suspender();
        }

        providerProfileRepository.save(profile);

        // M12: alerta ao admin quando verificacão é reprovada/inconclusiva
        if (action == ModerationAction.REPROVAR) {
            notificationService.criarAlerta("VERIFICACAO", profile.getId());
        }
    }
}
