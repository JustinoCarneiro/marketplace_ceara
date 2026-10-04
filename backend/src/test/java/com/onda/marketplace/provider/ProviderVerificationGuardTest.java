package com.onda.marketplace.provider;

import com.onda.marketplace.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Só prestador VERIFICADO opera. Até aqui a "aprovação manual" do admin só mudava o status no
 * banco: prestador em verificação, reprovado ou suspenso mandava proposta e recebia mesmo assim.
 */
@ExtendWith(MockitoExtension.class)
class ProviderVerificationGuardTest {

    private static final UUID PRESTADOR_ID = UUID.randomUUID();

    @Mock ProviderProfileRepository profileRepository;

    ProviderVerificationGuard guard;

    @BeforeEach
    void setUp() {
        guard = new ProviderVerificationGuard(profileRepository);
    }

    private void prestadorCom(ProviderStatus status) {
        when(profileRepository.findByUserId(PRESTADOR_ID))
                .thenReturn(Optional.of(ProviderProfiles.comStatus(status)));
    }

    // ----- exigirVerificado: o próprio prestador (propor, iniciar o serviço) -----

    @Test
    void exigirVerificado_verificado_passa() {
        prestadorCom(ProviderStatus.VERIFICADO);

        assertThatCode(() -> guard.exigirVerificado(PRESTADOR_ID)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(value = ProviderStatus.class, names = "VERIFICADO", mode = EnumSource.Mode.EXCLUDE)
    void exigirVerificado_qualquerOutroStatus_recusa(ProviderStatus status) {
        prestadorCom(status);

        assertThatThrownBy(() -> guard.exigirVerificado(PRESTADOR_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PROVIDER_NOT_VERIFIED");
    }

    @Test
    void exigirVerificado_emVerificacao_explicaQueFaltaAprovacao() {
        prestadorCom(ProviderStatus.EM_VERIFICACAO);

        assertThatThrownBy(() -> guard.exigirVerificado(PRESTADOR_ID))
                .hasMessageContaining("em verificação")
                .hasMessageContaining("assim que for aprovado");
    }

    @Test
    void exigirVerificado_reprovado_explicaQueNaoFoiAprovado() {
        prestadorCom(ProviderStatus.REPROVADO);

        assertThatThrownBy(() -> guard.exigirVerificado(PRESTADOR_ID))
                .hasMessageContaining("não foi aprovado");
    }

    @Test
    void exigirVerificado_suspenso_explicaQueEstaSuspenso() {
        prestadorCom(ProviderStatus.SUSPENSO);

        assertThatThrownBy(() -> guard.exigirVerificado(PRESTADOR_ID))
                .hasMessageContaining("suspenso");
    }

    @Test
    void exigirVerificado_semPerfil_recusaComoNaoVerificado() {
        // Um código só para "não pode operar": quem consome a API (e o aceite do cliente, que já
        // fazia assim) não precisa tratar "perfil ausente" como um caso à parte.
        when(profileRepository.findByUserId(PRESTADOR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guard.exigirVerificado(PRESTADOR_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PROVIDER_NOT_VERIFIED")
                .hasMessageContaining("não foi encontrado");
    }

    // ----- exigirContratavel: o cliente aceitando a proposta (o status pode ter mudado depois dela) -----

    @Test
    void exigirContratavel_verificado_passa() {
        prestadorCom(ProviderStatus.VERIFICADO);

        assertThatCode(() -> guard.exigirContratavel(PRESTADOR_ID)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(value = ProviderStatus.class, names = "VERIFICADO", mode = EnumSource.Mode.EXCLUDE)
    void exigirContratavel_qualquerOutroStatus_recusaComMensagemParaOCliente(ProviderStatus status) {
        prestadorCom(status);

        assertThatThrownBy(() -> guard.exigirContratavel(PRESTADOR_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PROVIDER_NOT_VERIFIED")
                .hasMessageContaining("Escolha outra proposta")
                // o cliente não deve ler a mensagem escrita para o prestador ("Seu cadastro…")
                .hasMessageNotContaining("Seu cadastro");
    }

    @Test
    void exigirContratavel_semPerfil_recusa() {
        when(profileRepository.findByUserId(PRESTADOR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guard.exigirContratavel(PRESTADOR_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PROVIDER_NOT_VERIFIED");
    }
}
