package com.onda.marketplace.admin;

/**
 * Ação de moderação sobre o perfil do prestador (US26).
 *
 * <p>{@code CONCILIAR_CPF} (revisão cruzada, 2ª rodada): é o ato humano que resolve uma duplicata legada de CPF — o backfill marca o
 * perfil como não conciliado ({@code cpf_conciliado = false}) e ele para de operar e de aparecer na busca até o suporte decidir qual
 * conta é a verdadeira. Sem esta ação a marca era permanente: só um UPDATE manual no banco a desfazia. No painel, o botão "Conciliar CPF"
 * fica no perfil do prestador (a justificativa é obrigatória na tela e vai para o log de auditoria) e a lista ganha o filtro "CPF
 * duplicado"; a ação é {@code POST /admin/providers/{id}/moderate}. O campo {@code cpfConciliado} da lista ({@link ProviderAdminDto}) é o
 * que acende o aviso.
 */
public enum ModerationAction { APROVAR, REPROVAR, SUSPENDER, CONCILIAR_CPF }
