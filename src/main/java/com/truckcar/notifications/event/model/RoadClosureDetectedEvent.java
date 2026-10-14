package com.truckcar.notifications.event.model;

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
public class RoadClosureDetectedEvent {
    private String eventId;
    private UUID recipientUserId;
    private String location;
    private String detourDescription;
    private String expectedDuration;
    private Instant timestamp;
}
