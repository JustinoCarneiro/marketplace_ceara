package com.onda.marketplace.servicerequest;

import jakarta.validation.constraints.NotBlank;

/** Confirmação do cliente sobre a descrição final do pedido (pode ter editado a sugestão da IA). */
public record PublishRequest(@NotBlank String descricao) {}
