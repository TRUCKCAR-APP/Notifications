package com.truckcar.notifications.event.consumer;

import com.truckcar.notifications.event.model.RoadClosureDetectedEvent;
import com.truckcar.notifications.event.model.RouteAlertConfirmedEvent;
import com.truckcar.notifications.model.NotificationCategory;
import com.truckcar.notifications.model.NotificationChannel;
import com.truckcar.notifications.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Consumer dedicated to high-priority safety road alerts requiring immediate voice delivery to drivers.
 * Uses highPriorityKafkaListenerContainerFactory for prioritized execution.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HighPriorityEventConsumer {

    private final NotificationService notificationService;

    @KafkaListener(
            topics = "${app.kafka.topics.route-alert:truckcar.route.alerts}",
            groupId = "${spring.kafka.consumer.group-id:notifications-group}-high-priority",
            containerFactory = "highPriorityKafkaListenerContainerFactory"
    )
    public void consumeRouteAlert(RouteAlertConfirmedEvent event) {
        log.info("HIGH PRIORITY EVENT received: RouteAlertConfirmedEvent id='{}', alertType='{}' for user='{}'",
                event.getEventId(), event.getAlertType(), event.getRecipientUserId());

        Map<String, Object> vars = new HashMap<>();
        vars.put("alertType", event.getAlertType());
        vars.put("roadSegment", event.getRoadSegment());
        vars.put("description", event.getDescription());
        vars.put("severity", event.getSeverity());

        notificationService.processEventNotification(
                event.getRecipientUserId(),
                NotificationCategory.ALERTA_RUTA,
                NotificationChannel.VOICE,
                event.getEventId(),
                "RouteAlertConfirmedEvent",
                "es-CO",
                vars,
                null
        );
    }

    @KafkaListener(
            topics = "${app.kafka.topics.road-closure:truckcar.route.closures}",
            groupId = "${spring.kafka.consumer.group-id:notifications-group}-high-priority",
            containerFactory = "highPriorityKafkaListenerContainerFactory"
    )
    public void consumeRoadClosure(RoadClosureDetectedEvent event) {
        log.info("HIGH PRIORITY EVENT received: RoadClosureDetectedEvent id='{}', location='{}' for user='{}'",
                event.getEventId(), event.getLocation(), event.getRecipientUserId());

        Map<String, Object> vars = new HashMap<>();
        vars.put("location", event.getLocation());
        vars.put("detourDescription", event.getDetourDescription());
        vars.put("expectedDuration", event.getExpectedDuration());

        notificationService.processEventNotification(
                event.getRecipientUserId(),
                NotificationCategory.CIERRE_VIAL,
                NotificationChannel.VOICE,
                event.getEventId(),
                "RoadClosureDetectedEvent",
                "es-CO",
                vars,
                null
        );
    }
}
