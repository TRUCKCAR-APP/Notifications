package com.truckcar.notifications.event.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.truckcar.notifications.event.model.EventEnvelope;
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
import java.util.UUID;

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

    /**
     * Botón SOS (alert.sos.triggered de Community-Alerts): push de emergencia por el tópico SNS al que está suscrita la
     * torre de control. El destinatario registrado es el conductor que pidió ayuda (el sujeto de la emergencia). Los
     * demás eventos del tópico (alert.sos.acknowledged) no generan notificación.
     */
    @KafkaListener(
            topics = "${app.kafka.topics.sos:truckcar.events.sos}",
            groupId = "${spring.kafka.consumer.group-id:notifications-group}-high-priority",
            containerFactory = "highPriorityKafkaListenerContainerFactory"
    )
    public void consumeSos(EventEnvelope event) {
        if (event == null || !"alert.sos.triggered".equals(event.getEventType())) {
            log.debug("Evento del tópico SOS sin notificación: type='{}'", event == null ? null : event.getEventType());
            return;
        }
        if (event.getEventId() == null || event.getPayload() == null) {
            throw new IllegalArgumentException("El evento SOS debe incluir eventId y payload");
        }
        JsonNode payload = event.getPayload();
        UUID driverId = RecipientIdResolver.resolve(text(payload, "driverId"));
        if (driverId == null) {
            throw new IllegalArgumentException("El evento SOS no trae un driverId válido");
        }
        log.warn("HIGH PRIORITY EVENT received: SOS id='{}' driver='{}'", event.getEventId(), driverId);

        // Un SOS puede llegar sin GPS: locationSource dice si la posición es del dispositivo (DEVICE_GPS), la última
        // conocida en fleets-drivers (LAST_KNOWN) o ninguna (UNKNOWN, sin coordenadas). Eventos anteriores no lo traen.
        String latitude = text(payload, "latitude");
        String longitude = text(payload, "longitude");
        boolean hasPosition = latitude != null && longitude != null;
        String source = text(payload, "locationSource");
        String location;
        if (!hasPosition) {
            location = "sin ubicación conocida: contacte al conductor";
        } else if ("LAST_KNOWN".equals(source)) {
            location = "sin GPS; última posición conocida " + latitude + ", " + longitude;
        } else {
            location = "en " + latitude + ", " + longitude;
        }
        Map<String, Object> vars = new HashMap<>();
        vars.put("driverName", orDash(text(payload, "driverName")));
        vars.put("vehiclePlate", orDash(text(payload, "vehiclePlate")));
        vars.put("latitude", hasPosition ? latitude : "desconocida");
        vars.put("longitude", hasPosition ? longitude : "desconocida");
        vars.put("location", location);
        vars.put("locationSource", source == null ? (hasPosition ? "DEVICE_GPS" : "UNKNOWN") : source);
        vars.put("loadId", orDash(text(payload, "loadId")));
        vars.put("sosId", text(payload, "id"));
        vars.put("mapsUrl", hasPosition
                ? "https://www.openstreetmap.org/?mlat=" + latitude + "&mlon=" + longitude + "#map=16/" + latitude + "/" + longitude
                : "—");

        notificationService.processEventNotification(
                driverId,
                NotificationCategory.EMERGENCIA_SOS,
                NotificationChannel.PUSH,
                event.getEventId().toString(),
                event.getEventType(),
                "es-CO",
                vars,
                null
        );
    }

    private static String text(JsonNode payload, String field) {
        JsonNode value = payload.get(field);
        return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }

    private static String orDash(String value) {
        return value == null ? "—" : value;
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
