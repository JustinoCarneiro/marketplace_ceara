package com.onda.marketplace.admin;

/**
 * Ação de moderação sobre o perfil do prestador (US26).
 *
 * <p>{@code CONCILIAR_CPF} (revisão cruzada, 2ª rodada): é o ato humano que resolve uma duplicata legada de CPF — o backfill marca o
 * perfil como não conciliado ({@code cpf_conciliado = false}) e ele para de operar e de aparecer na busca até o suporte decidir qual
 * conta é a verdadeira. Sem esta ação a marca era permanente: só um UPDATE manual no banco a desfazia. O painel ainda não tem o botão;
 * a ação existe na API ({@code POST /admin/providers/{id}/moderate}).
 */
public enum ModerationAction { APROVAR, REPROVAR, SUSPENDER, CONCILIAR_CPF }
