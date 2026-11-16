package com.truckcar.notifications.dto.request;

import com.truckcar.notifications.model.NotificationCategory;
import com.truckcar.notifications.model.NotificationChannel;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DirectNotificationSendRequest {

    @NotNull(message = "recipientUserId es obligatorio")
    private UUID recipientUserId;

    @NotNull(message = "category es obligatorio")
    private NotificationCategory category;

    @NotNull(message = "channel es obligatorio")
    private NotificationChannel channel;

    @Builder.Default
    private String language = "es-CO";

    private Map<String, Object> templateVariables;

    private String rawMessage;
}
