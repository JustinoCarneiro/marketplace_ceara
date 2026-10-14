package com.onda.marketplace.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Identificador do comprovante bancário; nunca CPF, chave Pix ou texto livre. */
public record ConfirmarRepasseManualRequest(
        @NotBlank
        @Size(max = 120)
        @Pattern(regexp = "[A-Za-z0-9._:/-]+")
        String comprovanteReferencia) {}
