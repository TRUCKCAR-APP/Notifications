package com.truckdar.notifications.event.consumer;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Normaliza identificadores de destinatario provenientes de otros servicios.
 * Acepta UUIDs (User-Identity) e identificadores de dominio como "driver-42" (fleets-drivers),
 * que se convierten en un UUID determinístico para conservar la columna recipient_user_id.
 */
final class RecipientIdResolver {

    private static final String NAMESPACE = "truckdar-recipient:";

    private RecipientIdResolver() {
    }

    static UUID resolve(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return null;
        }
        String value = rawId.trim();
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException notUuid) {
            return UUID.nameUUIDFromBytes((NAMESPACE + value).getBytes(StandardCharsets.UTF_8));
        }
    }
}
