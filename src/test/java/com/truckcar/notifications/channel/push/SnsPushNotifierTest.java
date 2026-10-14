package com.truckcar.notifications.channel.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SnsPushNotifierTest {

    @Mock
    private SnsClient snsClient;

    private SnsPushNotifier pushNotifier;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        pushNotifier = new SnsPushNotifier(snsClient, objectMapper);
        ReflectionTestUtils.setField(pushNotifier, "defaultTopicArn", "arn:aws:sns:us-east-1:123456789012:truckcar-test");
    }

    @Test
    @DisplayName("Should build structured JSON payload and publish to SNS")
    void shouldPublishToSnsSuccessfully() {
        UUID userId = UUID.randomUUID();
        when(snsClient.publish(any(PublishRequest.class)))
                .thenReturn(PublishResponse.builder().messageId("msg-abc-999").build());

        PushNotificationResult result = pushNotifier.sendPush(
                userId,
                "Alerta",
                "Cierre vial reportado",
                Map.of("alertId", "alt-123")
        );

        assertThat(result.success()).isTrue();
        assertThat(result.messageId()).isEqualTo("msg-abc-999");

        ArgumentCaptor<PublishRequest> captor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(snsClient, times(1)).publish(captor.capture());

        PublishRequest captured = captor.getValue();
        assertThat(captured.topicArn()).isEqualTo("arn:aws:sns:us-east-1:123456789012:truckcar-test");
        assertThat(captured.messageStructure()).isEqualTo("json");
        assertThat(captured.message()).contains("GCM");
        assertThat(captured.message()).contains("APNS");
    }
}
