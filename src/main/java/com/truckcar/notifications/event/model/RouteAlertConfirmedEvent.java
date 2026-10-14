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
public class RouteAlertConfirmedEvent {
    private String eventId;
    private UUID recipientUserId;
    private String alertType;
    private String roadSegment;
    private String description;
    private String severity;
    private Instant timestamp;
}
