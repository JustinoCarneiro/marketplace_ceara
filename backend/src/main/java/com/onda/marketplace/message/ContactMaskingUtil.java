package com.onda.marketplace.message;

import java.util.regex.Pattern;

/**
 * Mascaramento de contato no chat pré-transação (docs/BOAS_PRATICAS_UX.md §1) —
 * anti-desintermediação: sem isso, cliente e prestador combinam pagamento fora da
 * plataforma e a Onda perde a comissão que sustenta o MVP (CLAUDE.md: "Receita do MVP:
 * somente comissão"). Não é um filtro perfeito (número por extenso, foto de cartão etc.
 * escapam) — é a barreira de custo baixo que cobre o caso comum: copiar/colar um
 * telefone ou e-mail.
 */
final class ContactMaskingUtil {

    private ContactMaskingUtil() {}

    private static final Pattern EMAIL = Pattern.compile(
            "(?i)[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}");

    // DDI (+55) opcional, DDD (com/sem parênteses) opcional, número de 8 ou 9 dígitos com
    // separador opcional entre os blocos. Cobre "(85) 99999-9999", "85999999999",
    // "+55 85 99999-9999", "99999-9999" (sem DDD). Não usa "/" como separador válido — evita
    // casar datas (13/08/2026).
    private static final Pattern PHONE = Pattern.compile(
            "(?:\\+?55[\\s.-]?)?(?:\\(?\\d{2}\\)?[\\s.-]?)?9?\\d{4}[\\s.-]?\\d{4}\\b");

    record Resultado(String texto, boolean mascarado) {}

    static Resultado mascarar(String original) {
        if (original == null || original.isBlank()) {
            return new Resultado(original, false);
        }
        String semEmail = EMAIL.matcher(original).replaceAll("[contato removido]");
        String semTelefone = PHONE.matcher(semEmail).replaceAll("[contato removido]");
        return new Resultado(semTelefone, !semTelefone.equals(original));
    }
}
