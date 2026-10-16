package com.onda.marketplace.servicerequest;

import java.util.regex.Pattern;

/**
 * Sanitiza e mascara dados pessoais e sensíveis no relato do cliente antes do envio
 * para o provedor de IA (LGPD / minimização de dados).
 *
 * <p>Remove e-mails, telefones com/sem DDD, CPFs (formatados ou apenas dígitos) e
 * sequências numéricas longas (ex.: números de cartão de crédito).
 */
final class AiInputSanitizer {

    private AiInputSanitizer() {}

    private static final Pattern EMAIL = Pattern.compile(
            "(?i)\\b[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}\\b");

    // 4 blocos de 4 dígitos (ex.: 4111 2222 3333 4444) ou sequência contínua de 14 a 19 dígitos
    private static final Pattern CARD_OR_LONG_NUMBER = Pattern.compile(
            "(?<!\\d)(?:\\d{4}[ -]\\d{4}[ -]\\d{4}[ -]\\d{4}|\\d{14,19})(?!\\d)");

    private static final Pattern CPF_FORMATTED = Pattern.compile(
            "(?<!\\d)\\d{3}\\.\\d{3}\\.\\d{3}[.-]\\d{2}(?!\\d)");

    private static final Pattern PHONE = Pattern.compile(
            "(?<!\\d)(?:(?:\\+?55[\\s.-]*)?(?:\\(?[1-9]\\d\\)?[\\s.-]*)?(?:9\\d{4}|[2-8]\\d{3})[\\s.-]?\\d{4})(?!\\d)");

    private static final Pattern CPF_RAW = Pattern.compile(
            "(?<!\\d)\\d{11}(?!\\d)");

    static String sanitizar(String entrada) {
        if (entrada == null || entrada.isBlank()) {
            return "";
        }
        String texto = EMAIL.matcher(entrada).replaceAll("[e-mail removido]");
        texto = CARD_OR_LONG_NUMBER.matcher(texto).replaceAll("[número removido]");
        texto = CPF_FORMATTED.matcher(texto).replaceAll("[cpf removido]");
        texto = PHONE.matcher(texto).replaceAll("[telefone removido]");
        texto = CPF_RAW.matcher(texto).replaceAll("[cpf removido]");
        return texto;
    }
}
