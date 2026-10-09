package com.onda.marketplace.admin;

import com.onda.marketplace.auth.User;
import com.onda.marketplace.auth.UserRepository;
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
    private final UserRepository            userRepository;
    private final NotificationService       notificationService;

    public ModerationService(ProviderProfileRepository providerProfileRepository,
                             UserRepository userRepository,
                             NotificationService notificationService) {
        this.providerProfileRepository = providerProfileRepository;
        this.userRepository            = userRepository;
        this.notificationService       = notificationService;
    }

    /**
     * Trava a linha do USUÁRIO antes de ler o perfil (revisão cruzada, 2ª rodada): é a mesma trava que a exclusão de conta
     * toma, e a exclusão anonimiza o perfil junto. Sem ela, uma exclusão que commitasse entre a leitura do perfil e o
     * {@code save} era desfeita — bio, CPF cifrado, chave Pix e status voltavam ao valor de antes da anonimização.
     */
    @Transactional
    public void moderar(UUID userId, ModerationAction action) {
        User user = userRepository.findByIdComTrava(userId)
                .orElseThrow(() -> new BusinessException("PROVIDER_NOT_FOUND",
                        "Prestador não encontrado."));
        if (user.isExcluido()) {
            throw new BusinessException("ACCOUNT_DELETED",
                    "A conta deste prestador foi excluída: não há o que moderar.");
        }

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
