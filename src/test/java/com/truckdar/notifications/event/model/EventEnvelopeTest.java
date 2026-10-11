package com.truckdar.notifications.event.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EventEnvelopeTest {

    @Test
    void deserializesStandardEnvelopeAndNestedPayload() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        UUID eventId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        String json = """
                {
                  "eventId": "%s",
                  "eventType": "assignment.proposed",
                  "occurredAt": "2026-10-10T23:00:00Z",
                  "correlationId": "%s",
                  "version": 1,
                  "producer": "ai-powered-fleet-allocation",
                  "payload": {
                    "recipientUserId": "11111111-1111-1111-1111-111111111111",
                    "loadId": 42,
                    "matchingScore": 96.5
                  }
                }
                """.formatted(eventId, correlationId);

        EventEnvelope envelope = mapper.readValue(json, EventEnvelope.class);

        assertThat(envelope.getEventId()).isEqualTo(eventId);
        assertThat(envelope.getEventType()).isEqualTo("assignment.proposed");
        assertThat(envelope.getOccurredAt()).isEqualTo(Instant.parse("2026-10-10T23:00:00Z"));
        assertThat(envelope.getCorrelationId()).isEqualTo(correlationId);
        assertThat(envelope.getVersion()).isEqualTo(1);
        assertThat(envelope.getPayload().path("loadId").asLong()).isEqualTo(42L);
        assertThat(envelope.getPayload().path("matchingScore").asDouble()).isEqualTo(96.5);
    }
}
