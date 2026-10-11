package com.truckdar.notifications.event.publisher;

import com.truckdar.notifications.event.model.NotificationFailedEvent;
import com.truckdar.notifications.event.model.NotificationSentEvent;
import com.truckdar.notifications.event.model.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.notification-sent:truckdar.notifications.sent}")
    private String notificationSentTopic;

    @Value("${app.kafka.topics.notification-failed:truckdar.notifications.failed}")
    private String notificationFailedTopic;

    @Value("${app.kafka.topics.assignment-notifications:truckdar.events.assignment-notifications}")
    private String assignmentNotificationsTopic;

    public void publishSent(NotificationSentEvent event) {
        log.info("Publishing NotificationSentEvent for notificationId: {}, recipient: {}",
                event.getNotificationId(), event.getRecipientUserId());
        try {
            kafkaTemplate.send(notificationSentTopic, event.getRecipientUserId().toString(), event);
        } catch (Exception e) {
            log.error("Failed to publish NotificationSentEvent to Kafka: {}", e.getMessage(), e);
        }
    }

    public void publishFailed(NotificationFailedEvent event) {
        log.warn("Publishing NotificationFailedEvent for notificationId: {}, recipient: {}, reason: {}",
                event.getNotificationId(), event.getRecipientUserId(), event.getFailureReason());
        try {
            kafkaTemplate.send(notificationFailedTopic, event.getRecipientUserId().toString(), event);
        } catch (Exception e) {
            log.error("Failed to publish NotificationFailedEvent to Kafka: {}", e.getMessage(), e);
        }
    }

    public void publishAssignmentNotified(String loadId, EventEnvelope event) {
        log.info("Publishing assignment.notified for load {} (eventId: {})", loadId, event.getEventId());
        kafkaTemplate.send(assignmentNotificationsTopic, loadId, event);
    }
}
