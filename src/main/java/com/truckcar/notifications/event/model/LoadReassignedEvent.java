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
public class LoadReassignedEvent {
    private String eventId;
    private UUID recipientUserId;
    private String loadId;
    private String route;
    private String origin;
    private String destination;
    private String reason;
    private Instant timestamp;
}
