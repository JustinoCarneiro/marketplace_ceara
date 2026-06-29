package com.onda.marketplace.category;

import jakarta.validation.constraints.NotBlank;

/**
 * Criação de categoria (US28). {@code slug} é opcional — se vazio, é derivado
 * do nome no service.
 */
public record CreateCategoryRequest(@NotBlank String nome, String slug) {}
