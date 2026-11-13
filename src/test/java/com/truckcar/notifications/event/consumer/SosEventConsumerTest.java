package com.truckcar.notifications.event.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.truckcar.notifications.event.model.EventEnvelope;
import com.truckcar.notifications.model.NotificationCategory;
import com.truckcar.notifications.model.NotificationChannel;
import com.truckcar.notifications.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class SosEventConsumerTest {

    private final NotificationService notifications = mock(NotificationService.class);
    private final HighPriorityEventConsumer consumer = new HighPriorityEventConsumer(notifications);
    private final ObjectMapper mapper = new ObjectMapper();

    private EventEnvelope envelope(String type, String payloadJson) throws Exception {
        return EventEnvelope.builder().eventId(UUID.randomUUID()).eventType(type).producer("community-alerts")
                .payload(mapper.readTree(payloadJson)).build();
    }

    @Test
    @SuppressWarnings("unchecked")
    void sosTriggersAnEmergencyPushWithTheDriverPositionAndMapLink() throws Exception {
        UUID driverId = UUID.randomUUID();
        EventEnvelope event = envelope("alert.sos.triggered", """
                {"id": 12, "driverId": "%s", "driverName": "Ana", "vehiclePlate": "TDK-410",
                 "latitude": 4.61, "longitude": -74.08, "loadId": 30}""".formatted(driverId));

        consumer.consumeSos(event);

        ArgumentCaptor<Map<String, Object>> vars = ArgumentCaptor.forClass(Map.class);
        verify(notifications).processEventNotification(eq(driverId), eq(NotificationCategory.EMERGENCIA_SOS),
                eq(NotificationChannel.PUSH), eq(event.getEventId().toString()), eq("alert.sos.triggered"), eq("es-CO"),
                vars.capture(), isNull());
        assertThat(vars.getValue()).containsEntry("driverName", "Ana").containsEntry("vehiclePlate", "TDK-410")
                .containsEntry("latitude", "4.61").containsEntry("loadId", "30");
        assertThat((String) vars.getValue().get("mapsUrl")).contains("mlat=4.61").contains("mlon=-74.08");
    }

    @Test
    @SuppressWarnings("unchecked")
    void anSosWithTheLastKnownPositionSaysSoInTheMessage() throws Exception {
        UUID driverId = UUID.randomUUID();
        consumer.consumeSos(envelope("alert.sos.triggered", """
                {"id": 13, "driverId": "%s", "latitude": 5.07, "longitude": -75.52,
                 "locationSource": "LAST_KNOWN"}""".formatted(driverId)));

        ArgumentCaptor<Map<String, Object>> vars = ArgumentCaptor.forClass(Map.class);
        verify(notifications).processEventNotification(eq(driverId), any(), any(), anyString(), anyString(),
                anyString(), vars.capture(), isNull());
        assertThat(vars.getValue()).containsEntry("locationSource", "LAST_KNOWN")
                .containsEntry("location", "sin GPS; última posición conocida 5.07, -75.52");
        assertThat((String) vars.getValue().get("mapsUrl")).contains("mlat=5.07");
    }

    @Test
    @SuppressWarnings("unchecked")
    void anSosWithoutAnyPositionStillNotifiesWithoutABrokenMapLink() throws Exception {
        UUID driverId = UUID.randomUUID();
        consumer.consumeSos(envelope("alert.sos.triggered", """
                {"id": 14, "driverId": "%s", "latitude": null, "longitude": null,
                 "locationSource": "UNKNOWN"}""".formatted(driverId)));

        ArgumentCaptor<Map<String, Object>> vars = ArgumentCaptor.forClass(Map.class);
        verify(notifications).processEventNotification(eq(driverId), eq(NotificationCategory.EMERGENCIA_SOS), any(),
                anyString(), anyString(), anyString(), vars.capture(), isNull());
        assertThat(vars.getValue()).containsEntry("location", "sin ubicación conocida: contacte al conductor")
                .containsEntry("latitude", "desconocida").containsEntry("mapsUrl", "—");
        assertThat(vars.getValue().values()).noneMatch(value -> String.valueOf(value).contains("null"));
    }

    @Test
    void attendingASosDoesNotNotify() throws Exception {
        consumer.consumeSos(envelope("alert.sos.acknowledged", "{\"id\": 12}"));

        verifyNoInteractions(notifications);
    }

    @Test
    void anSosWithoutAValidDriverGoesToTheDeadLetterTopic() throws Exception {
        EventEnvelope event = envelope("alert.sos.triggered", "{\"id\": 12, \"latitude\": 4.6, \"longitude\": -74}");

        assertThatThrownBy(() -> consumer.consumeSos(event)).isInstanceOf(IllegalArgumentException.class);
        verify(notifications, org.mockito.Mockito.never()).processEventNotification(any(), any(), any(), anyString(),
                anyString(), anyString(), any(), any());
    }
}
