package com.truckcar.notifications.channel.push;

import lombok.Builder;

import java.time.Instant;

@Builder
public record PushNotificationResult(
        boolean success,
        String messageId,
        String errorReason,
        Instant sentAt
) {
    public static PushNotificationResult ok(String messageId) {
        return PushNotificationResult.builder()
                .success(true)
                .messageId(messageId)
                .sentAt(Instant.now())
                .build();
    }

    public static PushNotificationResult failure(String errorReason) {
        return PushNotificationResult.builder()
                .success(false)
                .errorReason(errorReason)
                .sentAt(Instant.now())
                .build();
    }
}
