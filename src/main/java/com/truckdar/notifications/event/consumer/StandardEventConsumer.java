package com.truckdar.notifications.event.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.truckdar.notifications.event.model.EventEnvelope;
import com.truckdar.notifications.event.model.DispatcherMessageEvent;
import com.truckdar.notifications.event.model.LoadReassignedEvent;
import com.truckdar.notifications.event.model.TripStatusChangedEvent;
import com.truckdar.notifications.event.publisher.NotificationEventPublisher;
import com.truckdar.notifications.model.NotificationCategory;
import com.truckdar.notifications.model.NotificationChannel;
import com.truckdar.notifications.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Consumer for standard domain events (load reassignment, trip status updates, dispatcher direct messages).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StandardEventConsumer {

    private final NotificationService notificationService;
    private final NotificationEventPublisher notificationEventPublisher;

    @KafkaListener(
            topics = "${app.kafka.topics.restrictions:truckdar.events.road-restrictions}",
            groupId = "${spring.kafka.consumer.group-id:notifications-group}",
            containerFactory = "standardKafkaListenerContainerFactory"
    )
    public void consumeRestrictionEvent(EventEnvelope event) {
        if (event == null || event.getEventType() == null || event.getPayload() == null) {
            throw new IllegalArgumentException("El evento de restricción debe incluir tipo y payload");
        }
        if (!"restriction.created".equals(event.getEventType())
                && !"route.updated".equals(event.getEventType())) {
            log.debug("Evento de restricción no notificable ignorado: type='{}'", event.getEventType());
            return;
        }

        JsonNode payload = event.getPayload();
        String recipient = text(payload, "recipientUserId", "assignedDriverId", "driverId");
        if (recipient == null) {
            log.info("Restricción recibida sin destinatario individual; se conserva para distribución posterior: eventId='{}'",
                    event.getEventId());
            return;
        }
        UUID recipientUserId = RecipientIdResolver.resolve(recipient);
        Map<String, Object> vars = new HashMap<>();
        vars.put("restrictionId", text(payload, "restrictionId", "restrictionCode"));
        vars.put("title", text(payload, "title"));
        vars.put("roadCorridor", text(payload, "roadCorridor"));
        vars.put("severity", text(payload, "severity"));

        notificationService.processEventNotification(
                recipientUserId,
                NotificationCategory.ALERTA_RUTA,
                NotificationChannel.PUSH,
                sourceEventId(event),
                event.getEventType(),
                "es-CO",
                vars,
                null
        );
    }

    @KafkaListener(
            topics = "${app.kafka.topics.load-events:truckdar.events.load-allocation}",
            groupId = "${spring.kafka.consumer.group-id:notifications-group}",
            containerFactory = "standardKafkaListenerContainerFactory"
    )
    public void consumeLoadEvent(EventEnvelope event) {
        if (event == null || event.getEventType() == null || event.getPayload() == null) {
            throw new IllegalArgumentException("El evento de carga debe incluir tipo y payload");
        }

        String eventType = event.getEventType();
        if ("load.published".equals(eventType)) {
            notifyLoadPublished(event);
        } else if ("load.assigned".equals(eventType)) {
            notifyLoadAssigned(event);
        } else if ("assignment.proposed".equals(eventType)) {
            notifyAssignmentProposed(event);
        } else {
            log.debug("Evento de carga no notificable ignorado: type='{}', eventId='{}'",
                    eventType, event.getEventId());
        }
    }

    private void notifyLoadPublished(EventEnvelope event) {
        JsonNode payload = event.getPayload();
        // La carga recién publicada aún no tiene conductor: se confirma a la empresa generadora.
        UUID recipientUserId = RecipientIdResolver.resolve(
                text(payload, "recipientUserId", "generatorCompanyId"));
        if (recipientUserId == null) {
            log.warn("load.published sin destinatario resoluble; no se notifica: eventId='{}'", event.getEventId());
            return;
        }
        Map<String, Object> vars = loadVariables(payload, event);

        notificationService.processEventNotification(
                recipientUserId,
                NotificationCategory.CAMBIO_ESTADO_VIAJE,
                NotificationChannel.PUSH,
                sourceEventId(event),
                event.getEventType(),
                "es-CO",
                vars,
                null
        );
    }

    private void notifyLoadAssigned(EventEnvelope event) {
        JsonNode payload = event.getPayload();
        UUID recipientUserId = recipientUserId(payload);
        Map<String, Object> vars = loadVariables(payload, event);
        vars.put("driverName", text(payload, "assignedDriverName"));
        vars.put("vehiclePlate", text(payload, "assignedVehiclePlate"));

        notificationService.processEventNotification(
                recipientUserId,
                NotificationCategory.REASIGNACION_CARGA,
                NotificationChannel.PUSH,
                sourceEventId(event),
                event.getEventType(),
                "es-CO",
                vars,
                null
        );
    }

    private void notifyAssignmentProposed(EventEnvelope event) {
        JsonNode payload = event.getPayload();
        UUID recipientUserId = recipientUserId(payload);
        Map<String, Object> vars = loadVariables(payload, event);
        vars.put("matchingScore", number(payload, "matchingScore"));
        vars.put("driverName", text(payload, "driverName", "assignedDriverName"));
        vars.put("vehiclePlate", text(payload, "vehiclePlate", "assignedVehiclePlate"));

        notificationService.processEventNotification(
                recipientUserId,
                NotificationCategory.REASIGNACION_CARGA,
                NotificationChannel.PUSH,
                sourceEventId(event),
                event.getEventType(),
                "es-CO",
                vars,
                null
        );

        publishAssignmentNotified(event);
    }

    /**
     * Toda propuesta (inicial o reasignación tras agent.score.ready) llega como assignment.proposed desde
     * Load-Allocation, que es la autoridad del estado comercial; así se notifica una sola vez y en orden.
     */
    private void publishAssignmentNotified(EventEnvelope event) {
        JsonNode payload = event.getPayload();
        String loadId = text(payload, "loadId", "id");
        if (loadId == null) {
            throw new IllegalArgumentException("assignment.notified requiere loadId para preservar el orden por carga");
        }
        ObjectNode notifiedPayload = payload.deepCopy();
        if (!notifiedPayload.hasNonNull("assignedDriverId")
                && notifiedPayload.hasNonNull("driverId")) {
            notifiedPayload.set("assignedDriverId", notifiedPayload.get("driverId"));
        }
        EventEnvelope notifiedEvent = EventEnvelope.builder()
                .eventId(UUID.randomUUID())
                .eventType("assignment.notified")
                .occurredAt(java.time.Instant.now())
                .correlationId(event.getCorrelationId())
                .version(1)
                .producer("notifications")
                .payload(notifiedPayload)
                .build();
        notificationEventPublisher.publishAssignmentNotified(loadId, notifiedEvent);
    }

    private UUID recipientUserId(JsonNode payload) {
        UUID recipientUserId = RecipientIdResolver.resolve(
                text(payload, "recipientUserId", "assignedDriverId", "driverId"));
        if (recipientUserId == null) {
            throw new IllegalArgumentException("El payload no contiene recipientUserId");
        }
        return recipientUserId;
    }

    private Map<String, Object> loadVariables(JsonNode payload, EventEnvelope event) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("loadId", text(payload, "loadId", "id"));
        vars.put("origin", text(payload, "originCity", "origin"));
        vars.put("destination", text(payload, "destinationCity", "destination"));
        vars.put("cargoDescription", text(payload, "cargoDescription"));
        vars.put("weightTons", number(payload, "weightTons"));
        vars.put("correlationId", event.getCorrelationId());
        return vars;
    }

    private String sourceEventId(EventEnvelope event) {
        if (event.getEventId() == null) {
            throw new IllegalArgumentException("El sobre debe incluir eventId");
        }
        return event.getEventId().toString();
    }

    private String text(JsonNode payload, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = payload.get(fieldName);
            if (value != null && !value.isNull() && !value.asText().isBlank()) {
                return value.asText();
            }
        }
        return null;
    }

    private Double number(JsonNode payload, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = payload.get(fieldName);
            if (value != null && value.isNumber()) {
                return value.asDouble();
            }
        }
        return null;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.load-reassigned:truckdar.loads.reassigned}",
            groupId = "${spring.kafka.consumer.group-id:notifications-group}",
            containerFactory = "standardKafkaListenerContainerFactory"
    )
    public void consumeLoadReassigned(LoadReassignedEvent event) {
        log.info("STANDARD EVENT received: LoadReassignedEvent id='{}', loadId='{}' for user='{}'",
                event.getEventId(), event.getLoadId(), event.getRecipientUserId());

        Map<String, Object> vars = new HashMap<>();
        vars.put("loadId", event.getLoadId());
        vars.put("ruta", event.getRoute());
        vars.put("route", event.getRoute());
        vars.put("origin", event.getOrigin());
        vars.put("destination", event.getDestination());
        vars.put("reason", event.getReason());

        notificationService.processEventNotification(
                event.getRecipientUserId(),
                NotificationCategory.REASIGNACION_CARGA,
                NotificationChannel.PUSH,
                event.getEventId(),
                "LoadReassignedEvent",
                "es-CO",
                vars,
                null
        );
    }

    @KafkaListener(
            topics = "${app.kafka.topics.trip-status:truckdar.trips.status}",
            groupId = "${spring.kafka.consumer.group-id:notifications-group}",
            containerFactory = "standardKafkaListenerContainerFactory"
    )
    public void consumeTripStatusChanged(TripStatusChangedEvent event) {
        log.info("STANDARD EVENT received: TripStatusChangedEvent id='{}', tripId='{}', status='{}' for user='{}'",
                event.getEventId(), event.getTripId(), event.getStatus(), event.getRecipientUserId());

        Map<String, Object> vars = new HashMap<>();
        vars.put("tripId", event.getTripId());
        vars.put("status", event.getStatus());
        vars.put("location", event.getLocation());

        notificationService.processEventNotification(
                event.getRecipientUserId(),
                NotificationCategory.CAMBIO_ESTADO_VIAJE,
                NotificationChannel.PUSH,
                event.getEventId(),
                "TripStatusChangedEvent",
                "es-CO",
                vars,
                null
        );
    }

    @KafkaListener(
            topics = "${app.kafka.topics.dispatcher-message:truckdar.dispatch.messages}",
            groupId = "${spring.kafka.consumer.group-id:notifications-group}",
            containerFactory = "standardKafkaListenerContainerFactory"
    )
    public void consumeDispatcherMessage(DispatcherMessageEvent event) {
        log.info("STANDARD EVENT received: DispatcherMessageEvent id='{}', from='{}' for user='{}'",
                event.getEventId(), event.getDispatcherName(), event.getRecipientUserId());

        Map<String, Object> vars = new HashMap<>();
        vars.put("dispatcherName", event.getDispatcherName());
        vars.put("message", event.getMessage());

        // Dispatcher messages can be delivered via Push and/or Voice according to configuration/event
        if (event.isSendPush()) {
            notificationService.processEventNotification(
                    event.getRecipientUserId(),
                    NotificationCategory.MENSAJE_DESPACHADOR,
                    NotificationChannel.PUSH,
                    event.getEventId() + "-push",
                    "DispatcherMessageEvent",
                    "es-CO",
                    vars,
                    null
            );
        }

        if (event.isSendVoice()) {
            notificationService.processEventNotification(
                    event.getRecipientUserId(),
                    NotificationCategory.MENSAJE_DESPACHADOR,
                    NotificationChannel.VOICE,
                    event.getEventId() + "-voice",
                    "DispatcherMessageEvent",
                    "es-CO",
                    vars,
                    null
            );
        }
    }
}
