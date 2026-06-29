package com.onda.marketplace.category;

/**
 * Atualização parcial de categoria (US28). Campos nulos são ignorados:
 * {@code nome} renomeia; {@code ativa} ativa/desativa.
 */
public record UpdateCategoryRequest(String nome, Boolean ativa) {}
