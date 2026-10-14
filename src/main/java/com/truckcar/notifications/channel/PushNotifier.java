package com.truckcar.notifications.channel;

import com.truckcar.notifications.channel.push.PushNotificationResult;

import java.util.Map;
import java.util.UUID;

public interface PushNotifier {

    /**
     * Sends a push notification to a user device/app.
     *
     * @param recipientUserId Identifier of the target user
     * @param title Title of the push notification
     * @param body Body text rendered
     * @param metadata Optional metadata to attach to the push payload
     * @return Result of the push delivery
     */
    PushNotificationResult sendPush(UUID recipientUserId, String title, String body, Map<String, Object> metadata);
}
