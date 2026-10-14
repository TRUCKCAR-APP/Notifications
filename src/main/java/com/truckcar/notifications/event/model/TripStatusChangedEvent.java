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
public class TripStatusChangedEvent {
    private String eventId;
    private UUID recipientUserId;
    private String tripId;
    private String status;
    private String location;
    private Instant timestamp;
}
