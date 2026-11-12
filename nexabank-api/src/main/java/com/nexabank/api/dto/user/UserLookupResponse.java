package com.nexabank.api.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Respuesta mínima para GET /api/v1/users/lookup?phone= — resuelve el
 * destinatario de una transferencia por teléfono sin exponer el perfil
 * completo del otro usuario (ver UserResponse). Deliberadamente no incluye
 * phone/email/roles/status — solo lo que el flujo de transferencia necesita:
 * el accountId destino y un nombre para mostrar en la confirmación.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserLookupResponse {

    private String accountId;
    private String displayName;
}
