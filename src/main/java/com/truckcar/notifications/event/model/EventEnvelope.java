package com.truckcar.notifications.event.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Sobre común de los eventos. Lector tolerante: un campo nuevo del productor no manda el evento al DLT. */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventEnvelope {

    private UUID eventId;
    private String eventType;
    private Instant occurredAt;
    private UUID correlationId;
    private int version;
    private String producer;
    private JsonNode payload;
}
