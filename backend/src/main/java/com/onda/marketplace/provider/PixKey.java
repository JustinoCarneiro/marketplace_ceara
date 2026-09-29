package com.onda.marketplace.provider;

import com.onda.marketplace.shared.exception.BusinessException;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Chave Pix validada e normalizada. É o destino de dinheiro real no repasse: uma chave
 * digitada errada paga a pessoa errada, ou só falha depois de o serviço já ter sido
 * concluído. Por isso valida formato e dígito verificador na entrada e deriva o tipo
 * (o Money Out do Mercado Pago exige o tipo da chave).
 *
 * <p>Formatos do DICT (Banco Central): CPF (11 dígitos), CNPJ (14 caracteres — alfanumérico
 * para os CNPJs emitidos desde julho/2026), e-mail (até 77), telefone (+55 DDD 9 dígitos)
 * e chave aleatória (UUID). Telefone só é aceito com "+55": 11 dígitos sem prefixo são
 * ambíguos com CPF, e é exatamente por isso que o DICT exige o prefixo.
 *
 * <p>Não prova titularidade — só que a chave é bem formada. Titularidade depende de consulta
 * ao DICT (a confirmar com o Mercado Pago).
 */
public record PixKey(Tipo tipo, String valor) {

    public enum Tipo { CPF, CNPJ, EMAIL, PHONE, ALEATORIA }

    private static final Pattern EMAIL =
            Pattern.compile("^[a-z0-9._%+\\-]+@[a-z0-9\\-]+(\\.[a-z0-9\\-]+)*\\.[a-z]{2,}$");
    private static final Pattern ALEATORIA =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    private static final Pattern TELEFONE = Pattern.compile("^\\+55[1-9][0-9]9[0-9]{8}$");
    private static final Pattern CPF = Pattern.compile("^[0-9]{11}$");
    private static final Pattern CNPJ = Pattern.compile("^[0-9A-Z]{12}[0-9]{2}$");

    private static final String MENSAGEM =
            "Chave Pix inválida. Use CPF, CNPJ, e-mail, telefone com +55 e DDD "
                    + "(ex.: +5585999999999) ou chave aleatória.";

    public static PixKey parse(String bruto) {
        if (bruto == null || bruto.isBlank()) {
            throw invalida();
        }
        String s = bruto.trim();

        if (s.contains("@")) {
            String email = s.toLowerCase(Locale.ROOT);
            if (email.length() <= 77 && EMAIL.matcher(email).matches()) {
                return new PixKey(Tipo.EMAIL, email);
            }
            throw invalida();
        }

        String minusculo = s.toLowerCase(Locale.ROOT);
        if (ALEATORIA.matcher(minusculo).matches()) {
            return new PixKey(Tipo.ALEATORIA, minusculo);
        }

        if (s.startsWith("+")) {
            String telefone = "+" + s.substring(1).replaceAll("[\\s().\\-]", "");
            if (TELEFONE.matcher(telefone).matches()) {
                return new PixKey(Tipo.PHONE, telefone);
            }
            throw invalida();
        }

        String compacto = s.replaceAll("[\\s.\\-/]", "").toUpperCase(Locale.ROOT);
        if (CPF.matcher(compacto).matches() && cpfValido(compacto)) {
            return new PixKey(Tipo.CPF, compacto);
        }
        if (CNPJ.matcher(compacto).matches() && cnpjValido(compacto)) {
            return new PixKey(Tipo.CNPJ, compacto);
        }
        throw invalida();
    }

    private static BusinessException invalida() {
        return new BusinessException("PIX_KEY_INVALID", MENSAGEM);
    }

    private static boolean todosIguais(String s) {
        return s.chars().distinct().count() == 1;
    }

    private static boolean cpfValido(String d) {
        return !todosIguais(d) && cpfDigito(d, 9) == d.charAt(9) - '0'
                && cpfDigito(d, 10) == d.charAt(10) - '0';
    }

    private static int cpfDigito(String d, int n) {
        int soma = 0;
        for (int i = 0; i < n; i++) {
            soma += (d.charAt(i) - '0') * (n + 1 - i);
        }
        int resto = (soma * 10) % 11;
        return resto == 10 ? 0 : resto;
    }

    // Módulo 11 com o valor do caractere = código ASCII − 48 (regra da Receita, que vale
    // igual para CNPJ numérico e alfanumérico).
    private static boolean cnpjValido(String d) {
        return !todosIguais(d) && cnpjDigito(d, 12) == d.charAt(12) - '0'
                && cnpjDigito(d, 13) == d.charAt(13) - '0';
    }

    private static int cnpjDigito(String d, int n) {
        int peso = 2;
        int soma = 0;
        for (int i = n - 1; i >= 0; i--) {
            soma += (d.charAt(i) - '0') * peso;
            peso = peso == 9 ? 2 : peso + 1;
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }
}
