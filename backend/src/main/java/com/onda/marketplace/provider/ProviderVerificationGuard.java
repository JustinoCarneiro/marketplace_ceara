package com.onda.marketplace.provider;

import com.onda.marketplace.shared.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Regra única de quem pode operar na plataforma: só prestador {@link ProviderStatus#VERIFICADO}
 * envia proposta, é contratado e inicia serviço.
 *
 * <p>Antes, a aprovação/reprovação/suspensão do admin ({@code ModerationService}) só gravava o
 * status: ele filtrava a busca por proximidade, mas prestador em verificação, reprovado ou
 * suspenso continuava propondo e, aceito, recebendo. Os dois métodos abaixo fecham isso nos
 * pontos em que o dinheiro entra no fluxo. A mensagem é mostrada ao usuário como está.
 */
@Component
@SuppressWarnings("null")
public class ProviderVerificationGuard {

    /** Código devolvido ao app e aos testes; o HTTP é 422, como todo {@link BusinessException}. */
    public static final String CODE = "PROVIDER_NOT_VERIFIED";

    private final ProviderProfileRepository profileRepository;

    public ProviderVerificationGuard(ProviderProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    /** Para o próprio prestador: enviar proposta e iniciar o serviço. */
    public void exigirVerificado(UUID prestadorId) {
        // Sem perfil (dado inconsistente: o cadastro sempre cria um) também não opera, e com o mesmo
        // código: quem consome a API trata um caso só — "não pode operar" — e o aceite já fazia assim.
        ProviderProfile profile = profileRepository.findByUserId(prestadorId)
                .orElseThrow(() -> new BusinessException(CODE,
                        "Seu cadastro de prestador não foi encontrado. Fale com o suporte."));
        // Achado da revisão cruzada (2026-10-05): duplicata legada (o CPF dele já tem dono sob outra conta) continuava
        // VERIFICADA e apta a propor — o self-hire só compara IDs de conta, nunca enxerga que é a MESMA pessoa.
        if (!profile.isCpfConciliado()) {
            throw new BusinessException(CODE,
                    "Seu CPF também está vinculado a outra conta nesta plataforma. Fale com o suporte para resolver "
                            + "antes de operar.");
        }
        ProviderStatus status = profile.getStatusVerificacao();
        if (status == ProviderStatus.VERIFICADO) {
            return;
        }
        throw new BusinessException(CODE, switch (status) {
            case EM_VERIFICACAO -> "Seu cadastro ainda está em verificação. "
                    + "Você poderá operar na plataforma assim que for aprovado.";
            case REPROVADO -> "Seu cadastro não foi aprovado e por isso você não pode operar "
                    + "na plataforma. Fale com o suporte.";
            case SUSPENSO -> "Seu cadastro está suspenso. Fale com o suporte para voltar a operar.";
            case VERIFICADO -> throw new IllegalStateException("já tratado acima");
        });
    }

    /**
     * Para o cliente que aceita a proposta: o status pode ter mudado depois que ela foi enviada
     * (o admin reprova ou suspende). A mensagem fala com o cliente, não com o prestador.
     */
    public void exigirContratavel(UUID prestadorId) {
        boolean contratavel = profileRepository.findByUserId(prestadorId)
                .filter(ProviderProfile::isCpfConciliado)   // achado da revisão cruzada, 2026-10-05
                .map(ProviderProfile::getStatusVerificacao)
                .filter(status -> status == ProviderStatus.VERIFICADO)
                .isPresent();
        if (!contratavel) {
            throw new BusinessException(CODE,
                    "Este prestador não está disponível no momento. Escolha outra proposta.");
        }
    }
}
