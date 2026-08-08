package com.onda.marketplace.denuncia;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateDenunciaRequest(
        @NotNull  TipoDenuncia tipo,
        @NotNull  java.util.UUID alvoId,
        @NotBlank @Size(max = 60) String motivo,
        @Size(max = 2000) String detalhes
) {}
