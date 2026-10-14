package com.truckcar.notifications.service;

import com.truckcar.notifications.dto.request.DirectNotificationSendRequest;
import com.truckcar.notifications.dto.response.NotificationResponse;
import com.truckcar.notifications.model.NotificationCategory;
import com.truckcar.notifications.model.NotificationChannel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;
import java.util.UUID;

public interface NotificationService {

    NotificationResponse sendDirect(DirectNotificationSendRequest request);

    void processEventNotification(
            UUID recipientUserId,
            NotificationCategory category,
            NotificationChannel channel,
            String sourceEventId,
            String sourceEventType,
            String language,
            Map<String, Object> templateVariables,
            String rawMessage
    );

    Page<NotificationResponse> getNotificationsByUserId(UUID recipientUserId, Pageable pageable);

    NotificationResponse getNotificationById(UUID id);
}
