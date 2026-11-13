package com.truckcar.notifications.event.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.truckcar.notifications.event.model.EventEnvelope;
import com.truckcar.notifications.event.publisher.NotificationEventPublisher;
import com.truckcar.notifications.model.NotificationCategory;
import com.truckcar.notifications.model.NotificationChannel;
import com.truckcar.notifications.service.NotificationService;
import com.truckcar.notifications.template.TemplateResolver;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * El texto de las notificaciones de carga no puede salir con huecos: antes la propuesta llegaba como
 * "Tu carga 2 ha sido reasignada a la ruta . Motivo: ." porque el evento no traía {{ruta}} ni {{reason}}.
 */
class LoadEventTextTest {

    // Plantillas tal como quedan después de V1 + V3.
    private static final String LOAD_TEMPLATE = "Carga {{loadId}} ({{ruta}}): {{reason}}.";
    private static final String TRIP_TEMPLATE = "El estado de tu viaje {{tripId}} ha cambiado a {{status}} en {{location}}.";

    private final NotificationService notifications = mock(NotificationService.class);
    private final StandardEventConsumer consumer =
            new StandardEventConsumer(notifications, mock(NotificationEventPublisher.class));
    private final ObjectMapper mapper = new ObjectMapper();
    private final TemplateResolver resolver = new TemplateResolver();

    private EventEnvelope envelope(String type, String payloadJson) throws Exception {
        return EventEnvelope.builder().eventId(UUID.randomUUID()).eventType(type).producer("load-allocation")
                .payload(mapper.readTree(payloadJson)).build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> varsFor(NotificationCategory category) {
        ArgumentCaptor<Map<String, Object>> vars = ArgumentCaptor.forClass(Map.class);
        verify(notifications).processEventNotification(any(UUID.class), eq(category), eq(NotificationChannel.PUSH),
                anyString(), anyString(), eq("es-CO"), vars.capture(), any());
        return vars.getValue();
    }

    @Test
    void aProposalTellsTheDriverTheRouteAndHowLongHeHasToAnswer() throws Exception {
        UUID driverId = UUID.randomUUID();
        consumer.consumeLoadEvent(envelope("assignment.proposed", """
                {"loadId": 12, "driverId": "%s", "originCity": "Bogotá", "destinationCity": "Tunja",
                 "weightTons": 25, "matchingScore": 87.5, "driverName": "Andrés Rojas"}""".formatted(driverId)));

        String text = resolver.resolve(LOAD_TEMPLATE, varsFor(NotificationCategory.REASIGNACION_CARGA));

        assertThat(text).isEqualTo("Carga 12 (Bogotá → Tunja): se la propone el motor de asignación. "
                + "Tiene 5 minutos para aceptarla o rechazarla en la app.");
    }

    @Test
    void anAcceptedProposalTellsTheGeneratorWhoCarriesTheLoad() throws Exception {
        UUID generatorId = UUID.randomUUID();
        consumer.consumeLoadEvent(envelope("assignment.accepted", """
                {"loadId": 12, "generatorCompanyId": "%s", "assignedDriverId": "%s", "originCity": "Bogotá",
                 "destinationCity": "Tunja", "assignedDriverName": "Andrés Rojas", "assignedVehiclePlate": "DEM-410"}"""
                .formatted(generatorId, UUID.randomUUID())));

        ArgumentCaptor<UUID> recipient = ArgumentCaptor.forClass(UUID.class);
        verify(notifications).processEventNotification(recipient.capture(), eq(NotificationCategory.REASIGNACION_CARGA),
                eq(NotificationChannel.PUSH), anyString(), eq("assignment.accepted"), eq("es-CO"), any(), any());
        assertThat(recipient.getValue()).as("se avisa al generador, no al conductor que aceptó").isEqualTo(generatorId);
        assertThat(resolver.resolve(LOAD_TEMPLATE, varsFor(NotificationCategory.REASIGNACION_CARGA)))
                .isEqualTo("Carga 12 (Bogotá → Tunja): la aceptó Andrés Rojas (DEM-410).");
    }

    /** Load-Allocation nunca emite load.assigned: ya no genera ninguna notificación. */
    @Test
    void theNeverEmittedLoadAssignedEventIsIgnored() throws Exception {
        consumer.consumeLoadEvent(envelope("load.assigned", """
                {"loadId": 12, "recipientUserId": "%s"}""".formatted(UUID.randomUUID())));

        org.mockito.Mockito.verifyNoInteractions(notifications);
    }

    @Test
    void aPublishedLoadConfirmsToTheGeneratorWithoutGaps() throws Exception {
        consumer.consumeLoadEvent(envelope("load.published", """
                {"loadId": 12, "generatorCompanyId": "%s", "originCity": "Bogotá", "destinationCity": "Tunja"}"""
                .formatted(UUID.randomUUID())));

        assertThat(resolver.resolve(TRIP_TEMPLATE, varsFor(NotificationCategory.CAMBIO_ESTADO_VIAJE)))
                .isEqualTo("El estado de tu viaje 12 ha cambiado a publicada en Bogotá → Tunja.");
    }

    @Test
    void aLoadWithoutCitiesStillReadsAsASentence() throws Exception {
        consumer.consumeLoadEvent(envelope("assignment.proposed", """
                {"loadId": 7, "driverId": "%s"}""".formatted(UUID.randomUUID())));

        assertThat(resolver.resolve(LOAD_TEMPLATE, varsFor(NotificationCategory.REASIGNACION_CARGA)))
                .startsWith("Carga 7 (sin ruta registrada): se la propone");
    }
}
