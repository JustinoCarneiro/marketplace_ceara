package com.onda.marketplace.auth;

import com.onda.marketplace.provider.CpfEncryptor;
import com.onda.marketplace.provider.ProviderCpfBackfill;
import com.onda.marketplace.provider.ProviderProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Esquecer a chave anterior na primeira subida depois da separação das chaves não dá erro nenhum: a unicidade deixa de
 * enxergar as contas antigas e quem já confirmou o CPF fica sem conseguir confirmar de novo. A aplicação recusa subir.
 *
 * <p>Revisão cruzada (2026-10-05): a conferência de versão não bastava — trocar o VALOR da chave sem subir a versão
 * também precisa ser recusado. Os testes novos cobrem o verificador por versão (gravado na 1ª subida, conferido nas
 * seguintes) e o cruzamento contra uma âncora real quando ela existe.
 */
@ExtendWith(MockitoExtension.class)
class CpfHashKeyCheckTest {

    static final String ATUAL    = "test-cpf-hmac-key-0123456789-0123456789";
    static final String ANTERIOR = "test-cpf-aes256-key-32-chars-here!";
    static final String OUTRA    = "outra-chave-completamente-diferente-32ch";

    @Mock UserRepository                 userRepository;
    @Mock ProviderProfileRepository      profileRepository;
    @Mock CpfEncryptor                   cpfEncryptor;
    @Mock CpfHashKeyVerificacaoRepository verificacaoRepository;

    /** Sem conta na versão (rollback) e sem âncora: as checagens novas ficam inertes, só a de versão conta. */
    private void semNadaDeNovo(int... versoes) {
        for (int v : versoes) {
            lenient().when(verificacaoRepository.findById(v)).thenReturn(Optional.empty());
        }
        lenient().when(profileRepository.comHashNaVersao(anyInt())).thenReturn(List.of());
        lenient().when(userRepository.countByCpfHashIsNotNullAndCpfHashVersaoGreaterThan(anyInt())).thenReturn(0L);
    }

    private CpfHashKeyCheck check(CpfHashService cpfHashService) {
        return new CpfHashKeyCheck(userRepository, cpfHashService, profileRepository, cpfEncryptor, verificacaoRepository, true);
    }

    @Test
    void contaNaVersaoAnterior_semAChaveAnteriorConfigurada_recusaSubir_eNaoDizONenhumCpf() {
        var check = check(new CpfHashService(ATUAL, 2));
        when(userRepository.countByCpfHashIsNotNullAndCpfHashVersaoLessThan(2)).thenReturn(3L);

        assertThatThrownBy(check::verificar)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("3 conta(s)")
                .hasMessageContaining("CPF_HASH_KEY_PREVIOUS");
    }

    @Test
    void comAChaveAnteriorConfigurada_aVersaoLogoAbaixoSeReconhece_enaoRecusa() {
        var check = check(new CpfHashService(ATUAL, 2, ANTERIOR));
        when(userRepository.countByCpfHashIsNotNullAndCpfHashVersaoLessThan(1)).thenReturn(0L);   // nada abaixo da 1
        semNadaDeNovo(1, 2);

        assertThatCode(check::verificar).doesNotThrowAnyException();
    }

    @Test
    void versaoMaisAntigaQueAAnterior_naoSeReconhece_aindaQueHajaChaveAnterior() {
        // rotação 1→2→3 sem regravar quem ficou na 1: a chave da versão 1 já não está configurada
        var check = check(new CpfHashService(ATUAL, 3, ANTERIOR));
        when(userRepository.countByCpfHashIsNotNullAndCpfHashVersaoLessThan(2)).thenReturn(1L);

        assertThatThrownBy(check::verificar).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void semContaAntiga_sobe() {
        var check = check(new CpfHashService(ATUAL, 2));
        when(userRepository.countByCpfHashIsNotNullAndCpfHashVersaoLessThan(2)).thenReturn(0L);
        semNadaDeNovo(2);

        assertThatCode(check::verificar).doesNotThrowAnyException();
    }

    // ── Revisão cruzada (2026-10-05): a chave pode mudar sem a versão mudar — a conferência de versão não via isso.

    @Test
    void versaoComVerificadorJaGravado_eAChaveEAMesma_sobe() {
        var cpfHashService = new CpfHashService(ATUAL, 2);
        var check = check(cpfHashService);
        when(userRepository.countByCpfHashIsNotNullAndCpfHashVersaoLessThan(2)).thenReturn(0L);
        when(userRepository.countByCpfHashIsNotNullAndCpfHashVersaoGreaterThan(2)).thenReturn(0L);
        when(verificacaoRepository.findById(2))
                .thenReturn(Optional.of(new CpfHashKeyVerificacao(2, cpfHashService.verificadorChaveAtual())));

        assertThatCode(check::verificar).doesNotThrowAnyException();
    }

    @Test
    void versaoComVerificadorJaGravado_masAChaveTrocou_recusaSubir() {
        // mesma versão (2), mas CPF_HASH_KEY mudou de valor por baixo: o verificador gravado não bate mais
        var check = check(new CpfHashService(ATUAL, 2));
        when(userRepository.countByCpfHashIsNotNullAndCpfHashVersaoLessThan(2)).thenReturn(0L);
        when(userRepository.countByCpfHashIsNotNullAndCpfHashVersaoGreaterThan(2)).thenReturn(0L);
        when(verificacaoRepository.findById(2))
                .thenReturn(Optional.of(new CpfHashKeyVerificacao(2, new CpfHashService(OUTRA, 2).verificadorChaveAtual())));

        assertThatThrownBy(check::verificar)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CPF_HASH_KEY")
                .hasMessageContaining("versão 2");
    }

    @Test
    void chaveAnteriorComVerificadorJaGravado_masAChaveTrocou_recusaSubir() {
        var check = check(new CpfHashService(ATUAL, 2, ANTERIOR));
        when(userRepository.countByCpfHashIsNotNullAndCpfHashVersaoLessThan(1)).thenReturn(0L);
        when(userRepository.countByCpfHashIsNotNullAndCpfHashVersaoGreaterThan(2)).thenReturn(0L);
        when(verificacaoRepository.findById(2)).thenReturn(Optional.of(new CpfHashKeyVerificacao(2, new CpfHashService(ATUAL, 2).verificadorChaveAtual())));
        when(verificacaoRepository.findById(1))
                .thenReturn(Optional.of(new CpfHashKeyVerificacao(1, new CpfHashService(OUTRA, 1).verificadorChaveAtual())));

        assertThatThrownBy(check::verificar)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CPF_HASH_KEY_PREVIOUS")
                .hasMessageContaining("versão 1");
    }

    @Test
    void primeiraSubidaDaVersao_semAncoraNenhuma_gravaOVerificador_confiaNaPrimeiraVez() {
        var cpfHashService = new CpfHashService(ATUAL, 2);
        var check = check(cpfHashService);
        when(userRepository.countByCpfHashIsNotNullAndCpfHashVersaoLessThan(2)).thenReturn(0L);
        when(userRepository.countByCpfHashIsNotNullAndCpfHashVersaoGreaterThan(2)).thenReturn(0L);
        when(verificacaoRepository.findById(2)).thenReturn(Optional.empty());
        when(profileRepository.comHashNaVersao(2)).thenReturn(List.of());   // nenhuma âncora decifrável

        assertThatCode(check::verificar).doesNotThrowAnyException();

        var captor = org.mockito.ArgumentCaptor.forClass(CpfHashKeyVerificacao.class);
        org.mockito.Mockito.verify(verificacaoRepository).save(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getVersao()).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getVerificador()).isEqualTo(cpfHashService.verificadorChaveAtual());
    }

    @Test
    void primeiraSubidaDaVersaoAnterior_comAncoraRealQueConfere_gravaOVerificador() {
        // a migração da separação (V25): a 1ª vez que a versão 1 (CPF_HASH_KEY_PREVIOUS) sobe, cruza contra um
        // prestador real cujo CPF decifrado prova que a chave informada é mesmo a antiga — não confia às cegas.
        var cpfHashService = new CpfHashService(ATUAL, 2, ANTERIOR);
        var check = check(cpfHashService);
        when(userRepository.countByCpfHashIsNotNullAndCpfHashVersaoLessThan(1)).thenReturn(0L);
        when(userRepository.countByCpfHashIsNotNullAndCpfHashVersaoGreaterThan(2)).thenReturn(0L);
        when(verificacaoRepository.findById(2))
                .thenReturn(Optional.of(new CpfHashKeyVerificacao(2, cpfHashService.verificadorChaveAtual())));
        when(verificacaoRepository.findById(1)).thenReturn(Optional.empty());

        UUID prestadorId = UUID.randomUUID();
        String cpf = "111.444.777-35";
        when(profileRepository.comHashNaVersao(1)).thenReturn(List.of(perfil(prestadorId, "cifrado-qualquer")));
        when(cpfEncryptor.decrypt("cifrado-qualquer")).thenReturn(cpf);
        User prestador = User.builder().nome("P").email("p@test.com").senhaHash("$2a$x").role(UserRole.ROLE_PROVIDER).build();
        prestador.vincularCpf(new CpfHashService(ANTERIOR, 1).hash(cpf), 1);
        when(userRepository.findById(prestadorId)).thenReturn(Optional.of(prestador));

        assertThatCode(check::verificar).doesNotThrowAnyException();
        org.mockito.Mockito.verify(verificacaoRepository).save(
                org.mockito.ArgumentMatchers.argThat(v -> v.getVersao() == 1));
    }

    @Test
    void primeiraSubidaDaVersaoAnterior_comAncoraRealQueNaoConfere_recusaSubir() {
        // CPF_HASH_KEY_PREVIOUS informado não é o valor que de fato gerou os hashes já gravados na versão 1
        var cpfHashService = new CpfHashService(ATUAL, 2, OUTRA);
        var check = check(cpfHashService);
        when(userRepository.countByCpfHashIsNotNullAndCpfHashVersaoLessThan(1)).thenReturn(0L);
        when(userRepository.countByCpfHashIsNotNullAndCpfHashVersaoGreaterThan(2)).thenReturn(0L);
        when(verificacaoRepository.findById(2))
                .thenReturn(Optional.of(new CpfHashKeyVerificacao(2, cpfHashService.verificadorChaveAtual())));
        when(verificacaoRepository.findById(1)).thenReturn(Optional.empty());

        UUID prestadorId = UUID.randomUUID();
        String cpf = "111.444.777-35";
        when(profileRepository.comHashNaVersao(1)).thenReturn(List.of(perfil(prestadorId, "cifrado-qualquer")));
        when(cpfEncryptor.decrypt("cifrado-qualquer")).thenReturn(cpf);
        User prestador = User.builder().nome("P").email("p@test.com").senhaHash("$2a$x").role(UserRole.ROLE_PROVIDER).build();
        prestador.vincularCpf(new CpfHashService(ANTERIOR, 1).hash(cpf), 1);   // hash de verdade é da ANTERIOR, não da OUTRA
        when(userRepository.findById(prestadorId)).thenReturn(Optional.of(prestador));

        assertThatThrownBy(check::verificar)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CPF_HASH_KEY_PREVIOUS");
    }

    @Test
    void cruzamentoDesativado_naoConsultaOPerfil_confiaNaPrimeiraSubida() {
        // onde providers_profile não existe (H2 dos testes de contexto, sem coluna geográfica) a mesma flag do
        // ProviderCpfBackfill desativa o cruzamento: sem ela, a 1ª subida de uma versão sempre quebraria ali.
        var cpfHashService = new CpfHashService(ATUAL, 2);
        var check = new CpfHashKeyCheck(userRepository, cpfHashService, profileRepository, cpfEncryptor, verificacaoRepository, false);
        when(userRepository.countByCpfHashIsNotNullAndCpfHashVersaoLessThan(2)).thenReturn(0L);
        when(userRepository.countByCpfHashIsNotNullAndCpfHashVersaoGreaterThan(2)).thenReturn(0L);
        when(verificacaoRepository.findById(2)).thenReturn(Optional.empty());

        assertThatCode(check::verificar).doesNotThrowAnyException();
        org.mockito.Mockito.verifyNoInteractions(profileRepository, cpfEncryptor);
    }

    @Test
    void contaComVersaoMaiorQueAConfigurada_recusaSubir_indicioDeRollback() {
        var check = check(new CpfHashService(ATUAL, 2));
        when(userRepository.countByCpfHashIsNotNullAndCpfHashVersaoLessThan(2)).thenReturn(0L);
        when(userRepository.countByCpfHashIsNotNullAndCpfHashVersaoGreaterThan(2)).thenReturn(1L);

        assertThatThrownBy(check::verificar)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MAIOR que a configurada");
    }

    private static ProviderCpfBackfill.PerfilSemHash perfil(UUID userId, String cpfCifrado) {
        return new ProviderCpfBackfill.PerfilSemHash() {
            public UUID getUserId() { return userId; }
            public String getCpfCifrado() { return cpfCifrado; }
        };
    }
}
