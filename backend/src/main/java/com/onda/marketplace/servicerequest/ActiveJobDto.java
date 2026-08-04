package com.onda.marketplace.servicerequest;

import java.util.UUID;

/** {@code requestId} nulo = prestador sem atendimento ACEITO/EM_ANDAMENTO agora. */
public record ActiveJobDto(UUID requestId) {}
