package com.onda.marketplace.message;

import java.time.Instant;
import java.util.UUID;

public record MessageDto(
        UUID    id,
        UUID    remetenteId,
        String  remetenteNome,
        String  conteudo,
        boolean mascarado,
        Instant createdAt
) {}
