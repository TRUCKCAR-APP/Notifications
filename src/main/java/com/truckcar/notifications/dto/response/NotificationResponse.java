package com.truckcar.notifications.dto.response;

import com.truckcar.notifications.model.NotificationCategory;
import com.truckcar.notifications.model.NotificationChannel;
import com.truckcar.notifications.model.NotificationStatus;
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
public class NotificationResponse {
    private UUID id;
    private UUID recipientUserId;
    private NotificationChannel channel;
    private NotificationCategory category;
    private String sourceEventId;
    private String sourceEventType;
    private String payload;
    private String audioUrl;
    private NotificationStatus status;
    private String failureReason;
    private Integer attempts;
    private Instant createdAt;
    private Instant sentAt;
}
