package com.truckcar.notifications.dto.response;

import com.truckcar.notifications.model.NotificationCategory;
import com.truckcar.notifications.model.NotificationChannel;
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
public class NotificationTemplateResponse {
    private UUID id;
    private NotificationCategory category;
    private String language;
    private NotificationChannel channel;
    private String templateText;
    private Instant createdAt;
    private Instant updatedAt;
}
