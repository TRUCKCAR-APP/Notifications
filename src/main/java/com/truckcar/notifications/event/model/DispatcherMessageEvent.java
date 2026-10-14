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
public class DispatcherMessageEvent {
    private String eventId;
    private UUID recipientUserId;
    private String dispatcherName;
    private String message;
    @Builder.Default
    private boolean sendVoice = true;
    @Builder.Default
    private boolean sendPush = true;
    private Instant timestamp;
}
