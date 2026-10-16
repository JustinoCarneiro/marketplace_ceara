package com.onda.marketplace.servicerequest;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiInputSanitizerTest {

    @Test
    void textoSemDadosPessoais_permaneceInalterado() {
        String texto = "Preciso consertar a fiação do chuveiro elétrico na cozinha.";
        assertThat(AiInputSanitizer.sanitizar(texto)).isEqualTo(texto);
    }

    @Test
    void nuloOuVazio_retornaVazio() {
        assertThat(AiInputSanitizer.sanitizar(null)).isEmpty();
        assertThat(AiInputSanitizer.sanitizar("   ")).isEmpty();
    }

    @Test
    void email_ehMascarado() {
        String texto = "Meu email é cliente_teste.123@gmail.com para contato.";
        assertThat(AiInputSanitizer.sanitizar(texto))
                .isEqualTo("Meu email é [e-mail removido] para contato.");
    }

    @Test
    void telefoneComDDD_ehMascarado() {
        assertThat(AiInputSanitizer.sanitizar("Ligue para (85) 98888-7777 urgente"))
                .isEqualTo("Ligue para [telefone removido] urgente");
        assertThat(AiInputSanitizer.sanitizar("WhatsApp: +55 85 98888-7777."))
                .isEqualTo("WhatsApp: [telefone removido].");
        assertThat(AiInputSanitizer.sanitizar("Tel 85988887777"))
                .isEqualTo("Tel [telefone removido]");
    }

    @Test
    void cpfComOuSemMascara_ehMascarado() {
        assertThat(AiInputSanitizer.sanitizar("CPF do titular 123.456.789-01 para nota"))
                .isEqualTo("CPF do titular [cpf removido] para nota");
        assertThat(AiInputSanitizer.sanitizar("CPF 12345678901"))
                .isEqualTo("CPF [cpf removido]");
    }

    @Test
    void numeroDeCartaoOuSequenciaLonga_ehMascarado() {
        assertThat(AiInputSanitizer.sanitizar("Cartão 4111 2222 3333 4444"))
                .isEqualTo("Cartão [número removido]");
        assertThat(AiInputSanitizer.sanitizar("Ref 1234567890123456"))
                .isEqualTo("Ref [número removido]");
    }

    @Test
    void multiplosDadosPessoaisNoMesmoRelato_saoTodosMascarados() {
        String texto = "Olá, sou Maria (maria@gmail.com, cel 85 99999-1111, cpf 012.345.678-99). Vazamento no banheiro.";
        String resultado = AiInputSanitizer.sanitizar(texto);
        assertThat(resultado)
                .doesNotContain("maria@gmail.com")
                .doesNotContain("99999-1111")
                .doesNotContain("012.345.678-99")
                .contains("[e-mail removido]")
                .contains("[telefone removido]")
                .contains("[cpf removido]")
                .contains("Vazamento no banheiro.");
    }
}

