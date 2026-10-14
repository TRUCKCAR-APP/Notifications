package com.truckcar.notifications.channel.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.truckcar.notifications.channel.PushNotifier;
import com.truckcar.notifications.exception.NotificationDeliveryException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class SnsPushNotifier implements PushNotifier {

    private final SnsClient snsClient;
    private final ObjectMapper objectMapper;

    @Value("${aws.sns.topic-arn:arn:aws:sns:us-east-1:123456789012:truckcar-notifications}")
    private String defaultTopicArn;

    @Override
    @Retry(name = "snsPush", fallbackMethod = "fallbackPush")
    @CircuitBreaker(name = "snsPush")
    public PushNotificationResult sendPush(UUID recipientUserId, String title, String body, Map<String, Object> metadata) {
        log.info("Sending SNS push notification to user {}: title='{}'", recipientUserId, title);

        try {
            String jsonPayload = buildSnsMessageJson(recipientUserId, title, body, metadata);

            PublishRequest publishRequest = PublishRequest.builder()
                    .topicArn(defaultTopicArn)
                    .message(jsonPayload)
                    .messageStructure("json")
                    .build();

            PublishResponse response = snsClient.publish(publishRequest);
            log.info("SNS Push sent successfully. MessageId: {}", response.messageId());
            return PushNotificationResult.ok(response.messageId());
        } catch (Exception e) {
            log.error("Failed to send push notification via SNS for user {}: {}", recipientUserId, e.getMessage(), e);
            throw new NotificationDeliveryException("SNS push delivery error: " + e.getMessage(), e);
        }
    }

    public PushNotificationResult fallbackPush(UUID recipientUserId, String title, String body, Map<String, Object> metadata, Throwable t) {
        log.error("Fallback triggered for SNS Push to user {}. Cause: {}", recipientUserId, t.getMessage());
        return PushNotificationResult.failure("Fallback triggered after retries: " + t.getMessage());
    }

    private String buildSnsMessageJson(UUID recipientUserId, String title, String body, Map<String, Object> metadata) {
        try {
            Map<String, Object> gcmData = new HashMap<>();
            if (metadata != null) {
                gcmData.putAll(metadata);
            }
            gcmData.put("recipientUserId", recipientUserId.toString());

            Map<String, Object> gcmNotification = Map.of(
                    "title", title,
                    "body", body
            );

            Map<String, Object> gcmRoot = Map.of(
                    "notification", gcmNotification,
                    "data", gcmData
            );

            Map<String, Object> apnsAps = Map.of(
                    "alert", Map.of("title", title, "body", body),
                    "sound", "default"
            );
            Map<String, Object> apnsRoot = new HashMap<>();
            apnsRoot.put("aps", apnsAps);
            if (metadata != null) {
                apnsRoot.putAll(metadata);
            }
            apnsRoot.put("recipientUserId", recipientUserId.toString());

            Map<String, String> root = Map.of(
                    "default", body,
                    "GCM", objectMapper.writeValueAsString(gcmRoot),
                    "APNS", objectMapper.writeValueAsString(apnsRoot)
            );

            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            log.warn("Error serializing SNS structured payload, falling back to plain text: {}", e.getMessage());
            return "{\"default\":\"" + body.replace("\"", "\\\"") + "\"}";
        }
    }
}
