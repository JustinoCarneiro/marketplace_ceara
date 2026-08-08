package com.onda.marketplace.denuncia;

import java.time.Instant;
import java.util.UUID;

/** Visão da fila de denúncias para o painel admin — resolve nomes pra moderador não decifrar UUID. */
public record DenunciaAdminDto(
        UUID id,
        String tipo,
        UUID alvoId,
        String alvoLabel,
        String denuncianteNome,
        String motivo,
        String detalhes,
        String status,
        Instant criadoEm
) {}
