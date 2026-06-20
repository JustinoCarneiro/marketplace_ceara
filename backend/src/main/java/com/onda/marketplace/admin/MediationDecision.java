package com.onda.marketplace.admin;

/**
 * Decisão da mediação de disputa (US24).
 * CONCLUIR  → libera o pagamento ao prestador (PAYMENT_RELEASED).
 * REEMBOLSAR → devolve o valor retido ao cliente (PAYMENT_REFUNDED).
 */
public enum MediationDecision { CONCLUIR, REEMBOLSAR }
