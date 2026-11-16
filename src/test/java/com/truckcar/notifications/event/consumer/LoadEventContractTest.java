package com.truckcar.notifications.event.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.truckcar.notifications.event.model.EventEnvelope;
import com.truckcar.notifications.event.publisher.NotificationEventPublisher;
import com.truckcar.notifications.model.NotificationCategory;
import com.truckcar.notifications.model.NotificationChannel;
import com.truckcar.notifications.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.kafka.support.converter.StringJsonMessageConverter;

import java.io.InputStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Contrato con Load-Allocation: contracts/load-allocation.load-event.v1.json es copia del fixture que Load-Allocation
 * verifica contra su outbox. Con el mismo ObjectMapper que arma Spring Boot (y el StringJsonMessageConverter de
 * KafkaConfig), un evento con campos que Notifications no conoce se procesa igual en lugar de ir al DLT.
 */
class LoadEventContractTest {

    private final ObjectMapper mapper = Jackson2ObjectMapperBuilder.json().build();

    private ObjectNode loadEvent(String type) throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/contracts/load-allocation.load-event.v1.json")) {
            ObjectNode event = (ObjectNode) mapper.readTree(in);
            event.put("eventType", type).put("campoFuturoDelSobre", "x").put("traceParent", "00-abc-def-01");
            ((ObjectNode) event.get("payload")).put("campoFuturo", 1);
            return event;
        }
    }

    @Test
    void aCancelledLoadWithFieldsWeDoNotKnowStillNotifiesTheDriver() throws Exception {
        NotificationService notifications = mock(NotificationService.class);
        StandardEventConsumer consumer = new StandardEventConsumer(notifications, mock(NotificationEventPublisher.class));
        EventEnvelope envelope = mapper.readValue(mapper.writeValueAsString(loadEvent("load.cancelled")), EventEnvelope.class);

        consumer.consumeLoadEvent(envelope);

        verify(notifications).processEventNotification(eq(RecipientIdResolver.resolve("driver-1")),
                eq(NotificationCategory.REASIGNACION_CARGA), eq(NotificationChannel.PUSH), anyString(),
                eq("load.cancelled"), eq("es-CO"), any(), any());
    }

    /** Por el mismo conversor que usan los listeners (KafkaConfig.recordMessageConverter). */
    @Test
    void theKafkaMessageConverterAcceptsTheEnvelopeWithExtraFields() throws Exception {
        NotificationService notifications = mock(NotificationService.class);
        StandardEventConsumer consumer = new StandardEventConsumer(notifications, mock(NotificationEventPublisher.class));
        StringJsonMessageConverter converter = new StringJsonMessageConverter(mapper);
        String json = mapper.writeValueAsString(loadEvent("assignment.proposed"));

        EventEnvelope envelope = (EventEnvelope) converter.toMessage(
                new org.apache.kafka.clients.consumer.ConsumerRecord<>("truckcar.events.load-allocation", 0, 0L, "95", json),
                null, null, EventEnvelope.class).getPayload();
        consumer.consumeLoadEvent(envelope);

        verify(notifications).processEventNotification(eq(RecipientIdResolver.resolve("driver-1")),
                eq(NotificationCategory.REASIGNACION_CARGA), eq(NotificationChannel.PUSH), anyString(),
                eq("assignment.proposed"), eq("es-CO"), any(), any());
    }
}
