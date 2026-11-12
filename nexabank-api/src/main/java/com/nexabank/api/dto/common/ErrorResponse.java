package com.nexabank.api.dto.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Formato estándar de errores de la API.
 *
 * <p>Toda la API utiliza esta estructura consistente.
 * Nunca devolver stack traces al cliente.
 *
 * <pre>
 * {
 *   "timestamp": "2026-09-18T03:00:00Z",
 *   "status": 422,
 *   "error": "INSUFFICIENT_FUNDS",
 *   "message": "Insufficient funds for this operation",
 *   "path": "/api/v1/transfers",
 *   "traceId": "abc123"
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    @Builder.Default
    private Instant timestamp = Instant.now();

    private int status;
    private String error;
    private String message;
    private String path;
    private String traceId;
}
