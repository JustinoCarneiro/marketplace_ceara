package com.onda.marketplace.auth;

import java.util.UUID;

/** Leitura mínima para listas; não expõe e-mail, CPF, senha ou papéis. */
public record UserName(UUID id, String nome) {}
