package com.onda.marketplace.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A chave do HMAC do CPF é própria (não a de cifra) e versionada: a rotação não pode perder a unicidade, e como só o
 * prestador (CPF cifrado) se refaz sozinho, a chave anterior fica configurada até não restar conta nela.
 */
class CpfHashServiceTest {

    static final String ATUAL    = "test-cpf-hmac-key-0123456789-0123456789";
    static final String ANTERIOR = "test-cpf-aes256-key-32-chars-here!";
    static final String CPF      = "11144477735";

    @Test
    void hash_eDeterministico_comOuSemMascara_eTemOTamanhoDoSha256() {
        var svc = new CpfHashService(ATUAL, 2);

        assertThat(svc.hash("111.444.777-35")).isEqualTo(svc.hash(CPF)).hasSize(64).doesNotContain("111");
    }

    @Test
    void chavesDiferentes_geramHashesDiferentes_ePortantoUmaChaveNaoReconheceAOutra() {
        assertThat(new CpfHashService(ATUAL, 2).hash(CPF)).isNotEqualTo(new CpfHashService(ANTERIOR, 1).hash(CPF));
    }

    @Test
    void hashesPossiveis_semRotacao_saoSoOAtual() {
        assertThat(new CpfHashService(ATUAL, 2).hashesPossiveis(CPF)).containsExactly(new CpfHashService(ATUAL, 2).hash(CPF));
    }

    @Test
    void hashesPossiveis_numaRotacao_incluemOAnterior_paraOCpfAindaNaoRegravadoTerDono() {
        var svc = new CpfHashService(ATUAL, 2, ANTERIOR);

        assertThat(svc.hashesPossiveis(CPF)).containsExactly(svc.hash(CPF), new CpfHashService(ANTERIOR, 1).hash(CPF));
    }

    @Test
    void confere_reconheceOHashDaVersaoAtual_eDaAnteriorQuandoAChaveEstaConfigurada() {
        var svc = new CpfHashService(ATUAL, 2, ANTERIOR);

        assertThat(svc.confere(CPF, svc.hash(CPF), 2)).isTrue();
        assertThat(svc.confere(CPF, new CpfHashService(ANTERIOR, 1).hash(CPF), 1)).isTrue();
    }

    @Test
    void confere_naoReconhece_cpfDiferente_hashNulo_nemVersaoSemChave() {
        var svc = new CpfHashService(ATUAL, 2, ANTERIOR);
        var semAnterior = new CpfHashService(ATUAL, 2);

        assertThat(svc.confere("52998224725", svc.hash(CPF), 2)).as("outro CPF").isFalse();
        assertThat(svc.confere(CPF, null, 2)).as("conta sem hash").isFalse();
        assertThat(semAnterior.confere(CPF, new CpfHashService(ANTERIOR, 1).hash(CPF), 1))
                .as("versão 1 sem a chave anterior configurada: não dá para conferir").isFalse();
        assertThat(svc.confere(CPF, svc.hash(CPF), 1)).as("o hash certo com a versão errada").isFalse();
    }

    @Test
    void temChaveAnterior_refleteAConfiguracao() {
        assertThat(new CpfHashService(ATUAL, 2, ANTERIOR).temChaveAnterior()).isTrue();
        assertThat(new CpfHashService(ATUAL, 2).temChaveAnterior()).isFalse();
    }

    @Test
    void recusaChaveCurta_versaoInvalida_eAnteriorIgualAAtual() {
        assertThatThrownBy(() -> new CpfHashService("curta", 2)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("32 caracteres");
        assertThatThrownBy(() -> new CpfHashService(null, 2)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CpfHashService(ATUAL, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CpfHashService(ATUAL, 2, ATUAL)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("não haveria o que rotacionar");
    }
}
