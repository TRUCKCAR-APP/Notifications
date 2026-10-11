package com.truckdar.notifications.event.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

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
