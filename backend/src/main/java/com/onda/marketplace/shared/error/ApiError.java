package com.onda.marketplace.shared.error;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;

/**
 * Envelope de erro padrão da API (TS06).
 * { timestamp, status, code, message, path }
 */
public record ApiError(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant timestamp,
        int status,
        String code,
        String message,
        String path
) {
    public static ApiError of(int status, String code, String message, String path) {
        return new ApiError(Instant.now(), status, code, message, path);
    }
}
