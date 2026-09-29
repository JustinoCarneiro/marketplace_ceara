package com.onda.marketplace.provider;

import com.onda.marketplace.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Chave Pix é o destino de dinheiro real no repasse: chave digitada errada paga a pessoa
 * errada (ou falha depois de o serviço já ter sido concluído). Validação de formato +
 * dígito verificador na entrada, e tipo derivado (o Money Out do Mercado Pago exige o tipo).
 * Valores de exemplo conferidos por cálculo independente do dígito verificador.
 */
class PixKeyTest {

    private static void assertInvalida(String bruto) {
        assertThatThrownBy(() -> PixKey.parse(bruto))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PIX_KEY_INVALID");
    }

    // ----- e-mail -----

    @Test
    void email_normalizaParaMinusculoESemEspacos() {
        PixKey chave = PixKey.parse("  Prestador@Pix.COM ");

        assertThat(chave.tipo()).isEqualTo(PixKey.Tipo.EMAIL);
        assertThat(chave.valor()).isEqualTo("prestador@pix.com");
    }

    @ParameterizedTest
    @ValueSource(strings = {"sem-arroba", "a@b", "a b@pix.com", "@pix.com", "a@pix..com"})
    void email_malFormado_ehRejeitado(String bruto) {
        assertInvalida(bruto);
    }

    // ----- CPF -----

    @ParameterizedTest
    @ValueSource(strings = {"111.444.777-35", "11144477735", " 111 444 777 35 "})
    void cpf_comOuSemPontuacao_normalizaParaSoDigitos(String bruto) {
        PixKey chave = PixKey.parse(bruto);

        assertThat(chave.tipo()).isEqualTo(PixKey.Tipo.CPF);
        assertThat(chave.valor()).isEqualTo("11144477735");
    }

    @ParameterizedTest
    @ValueSource(strings = {"111.444.777-36", "111.111.111-11", "00000000000"})
    void cpf_comDigitoVerificadorErradoOuRepetido_ehRejeitado(String bruto) {
        assertInvalida(bruto);
    }

    // ----- CNPJ -----

    @Test
    void cnpjNumerico_normalizaParaSoCaracteres() {
        PixKey chave = PixKey.parse("11.222.333/0001-81");

        assertThat(chave.tipo()).isEqualTo(PixKey.Tipo.CNPJ);
        assertThat(chave.valor()).isEqualTo("11222333000181");
    }

    @Test
    void cnpjAlfanumerico_formatoOficialDaReceitaDesde2026_ehAceito() {
        PixKey chave = PixKey.parse("12.abc.345/01de-35");

        assertThat(chave.tipo()).isEqualTo(PixKey.Tipo.CNPJ);
        assertThat(chave.valor()).isEqualTo("12ABC34501DE35");
    }

    @ParameterizedTest
    @ValueSource(strings = {"11.222.333/0001-82", "12ABC34501DE36", "11111111111111"})
    void cnpj_comDigitoVerificadorErradoOuRepetido_ehRejeitado(String bruto) {
        assertInvalida(bruto);
    }

    // ----- telefone -----

    @ParameterizedTest
    @ValueSource(strings = {"+5585999990000", "+55 (85) 99999-0000"})
    void telefone_comMais55_normalizaParaE164(String bruto) {
        PixKey chave = PixKey.parse(bruto);

        assertThat(chave.tipo()).isEqualTo(PixKey.Tipo.PHONE);
        assertThat(chave.valor()).isEqualTo("+5585999990000");
    }

    @Test
    void telefoneSemMais55_naoEhAdivinhado_eAMensagemOrientaOFormato() {
        // 11 dígitos sem "+55" é ambíguo com CPF — o DICT exige o "+55" justamente por isso.
        assertThatThrownBy(() -> PixKey.parse("85999990000"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PIX_KEY_INVALID")
                .hasMessageContaining("+55");
    }

    @ParameterizedTest
    @ValueSource(strings = {"+558532223333", "+558599999000", "+55859999900001"})
    void telefone_foraDoPadraoCelular_ehRejeitado(String bruto) {
        assertInvalida(bruto);
    }

    // ----- chave aleatória -----

    @Test
    void aleatoria_normalizaParaMinusculo() {
        PixKey chave = PixKey.parse("123E4567-E89B-12D3-A456-426614174000");

        assertThat(chave.tipo()).isEqualTo(PixKey.Tipo.ALEATORIA);
        assertThat(chave.valor()).isEqualTo("123e4567-e89b-12d3-a456-426614174000");
    }

    @Test
    void aleatoria_incompleta_ehRejeitada() {
        assertInvalida("123e4567-e89b-12d3-a456-42661417400");
    }

    // ----- vazio -----

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void vazioOuNulo_ehRejeitado(String bruto) {
        assertInvalida(bruto);
    }
}
