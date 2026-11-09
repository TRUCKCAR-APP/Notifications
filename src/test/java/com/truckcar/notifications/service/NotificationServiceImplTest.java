package com.truckcar.notifications.service;

import com.truckcar.notifications.channel.PushNotifier;
import com.truckcar.notifications.channel.VoiceSynthesizer;
import com.truckcar.notifications.channel.push.PushNotificationResult;
import com.truckcar.notifications.channel.voice.VoiceSynthesisResult;
import com.truckcar.notifications.dto.request.DirectNotificationSendRequest;
import com.truckcar.notifications.dto.response.NotificationResponse;
import com.truckcar.notifications.event.publisher.NotificationEventPublisher;
import com.truckcar.notifications.mapper.NotificationMapper;
import com.truckcar.notifications.model.Notification;
import com.truckcar.notifications.model.NotificationCategory;
import com.truckcar.notifications.model.NotificationChannel;
import com.truckcar.notifications.model.NotificationStatus;
import com.truckcar.notifications.repository.NotificationRepository;
import com.truckcar.notifications.service.impl.NotificationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationTemplateService templateService;

    @Mock
    private PushNotifier pushNotifier;

    @Mock
    private VoiceSynthesizer voiceSynthesizer;

    @Mock
    private NotificationEventPublisher eventPublisher;

    private NotificationMapper notificationMapper;

    private NotificationServiceImpl notificationService;

    @BeforeEach
    void setUp() {
        notificationMapper = Mappers.getMapper(NotificationMapper.class);
        notificationService = new NotificationServiceImpl(
                notificationRepository,
                templateService,
                pushNotifier,
                voiceSynthesizer,
                eventPublisher,
                notificationMapper
        );
    }

    @Test
    @DisplayName("Should send direct PUSH notification successfully")
    void shouldSendDirectPushSuccessfully() {
        UUID userId = UUID.randomUUID();
        DirectNotificationSendRequest request = DirectNotificationSendRequest.builder()
                .recipientUserId(userId)
                .category(NotificationCategory.REASIGNACION_CARGA)
                .channel(NotificationChannel.PUSH)
                .language("es-CO")
                .templateVariables(Map.of("loadId", "123", "ruta", "Bogotá - Cali"))
                .build();

        when(templateService.resolveTemplateText(eq(NotificationCategory.REASIGNACION_CARGA), eq(NotificationChannel.PUSH), eq("es-CO"), anyMap()))
                .thenReturn("Tu carga 123 ha sido reasignada a Bogotá - Cali.");

        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(inv -> {
                    Notification n = inv.getArgument(0);
                    if (n.getId() == null) {
                        n.setId(UUID.randomUUID());
                    }
                    return n;
                });

        when(pushNotifier.sendPush(eq(userId), anyString(), anyString(), anyMap()))
                .thenReturn(PushNotificationResult.ok("msg-sns-123"));

        NotificationResponse response = notificationService.sendDirect(request);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(response.getRecipientUserId()).isEqualTo(userId);
        assertThat(response.getPayload()).isEqualTo("Tu carga 123 ha sido reasignada a Bogotá - Cali.");

        verify(pushNotifier, times(1)).sendPush(eq(userId), anyString(), anyString(), anyMap());
        verify(eventPublisher, times(1)).publishSent(any());
        verify(eventPublisher, never()).publishFailed(any());
    }

    @Test
    @DisplayName("Should skip event notification when sourceEventId has already been processed (idempotency)")
    void shouldSkipDuplicateEventProcessing() {
        UUID userId = UUID.randomUUID();
        String sourceEventId = "evt-reassigned-999";

        when(notificationRepository.existsBySourceEventIdAndChannel(sourceEventId, NotificationChannel.PUSH))
                .thenReturn(true);

        notificationService.processEventNotification(
                userId,
                NotificationCategory.REASIGNACION_CARGA,
                NotificationChannel.PUSH,
                sourceEventId,
                "LoadReassignedEvent",
                "es-CO",
                Map.of("loadId", "999"),
                null
        );

        verify(notificationRepository, never()).save(any());
        verify(pushNotifier, never()).sendPush(any(), any(), any(), any());
        verify(eventPublisher, never()).publishSent(any());
    }

    @Test
    @DisplayName("Should process VOICE notification with Polly and publish sent event")
    void shouldProcessVoiceNotificationSuccessfully() {
        UUID userId = UUID.randomUUID();
        String sourceEventId = "alert-route-101";

        when(notificationRepository.existsBySourceEventIdAndChannel(sourceEventId, NotificationChannel.VOICE))
                .thenReturn(false);

        when(templateService.resolveTemplateText(eq(NotificationCategory.ALERTA_RUTA), eq(NotificationChannel.VOICE), eq("es-CO"), anyMap()))
                .thenReturn("Atención conductor: Derrumbe en km 45.");

        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(inv -> {
                    Notification n = inv.getArgument(0);
                    if (n.getId() == null) {
                        n.setId(UUID.randomUUID());
                    }
                    return n;
                });

        when(voiceSynthesizer.synthesizeAndUpload(anyString(), eq("es-CO")))
                .thenReturn(VoiceSynthesisResult.ok("https://s3.amazonaws.com/test-audio.mp3", "audio/es-CO/test.mp3", false));

        notificationService.processEventNotification(
                userId,
                NotificationCategory.ALERTA_RUTA,
                NotificationChannel.VOICE,
                sourceEventId,
                "RouteAlertConfirmedEvent",
                "es-CO",
                Map.of("alertType", "Derrumbe"),
                null
        );

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, atLeastOnce()).save(captor.capture());

        Notification finalNotification = captor.getValue();
        assertThat(finalNotification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(finalNotification.getAudioUrl()).isEqualTo("https://s3.amazonaws.com/test-audio.mp3");

        verify(eventPublisher, times(1)).publishSent(any());
    }

    @Test
    @DisplayName("Should execute VOICE fallback when PUSH delivery fails and succeed")
    void shouldFallbackToVoiceWhenPushFails() {
        UUID userId = UUID.randomUUID();
        DirectNotificationSendRequest request = DirectNotificationSendRequest.builder()
                .recipientUserId(userId)
                .category(NotificationCategory.ALERTA_RUTA)
                .channel(NotificationChannel.PUSH)
                .language("es-CO")
                .rawMessage("Alerta de desvío por cierre vial.")
                .build();

        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(inv -> {
                    Notification n = inv.getArgument(0);
                    if (n.getId() == null) {
                        n.setId(UUID.randomUUID());
                    }
                    return n;
                });

        when(pushNotifier.sendPush(eq(userId), anyString(), anyString(), any()))
                .thenReturn(PushNotificationResult.failure("EndpointDisabled: Device token unregistered"));

        when(voiceSynthesizer.synthesizeAndUpload(eq("Alerta de desvío por cierre vial."), eq("es-CO")))
                .thenReturn(VoiceSynthesisResult.ok("https://s3.amazonaws.com/fallback-voice.mp3", "audio/es-CO/fallback.mp3", false));

        NotificationResponse response = notificationService.sendDirect(request);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(response.getAudioUrl()).isEqualTo("https://s3.amazonaws.com/fallback-voice.mp3");
        assertThat(response.getFailureReason()).contains("PUSH_FALLBACK_VOICE");

        verify(pushNotifier, times(1)).sendPush(any(), any(), any(), any());
        verify(voiceSynthesizer, times(1)).synthesizeAndUpload(anyString(), anyString());
        verify(eventPublisher, times(1)).publishSent(any());
        verify(eventPublisher, never()).publishFailed(any());
    }

    @Test
    @DisplayName("Should mark FAILED when both PUSH and VOICE fallback fail")
    void shouldMarkFailedWhenBothPushAndVoiceFallbackFail() {
        UUID userId = UUID.randomUUID();
        DirectNotificationSendRequest request = DirectNotificationSendRequest.builder()
                .recipientUserId(userId)
                .category(NotificationCategory.EMERGENCIA_SOS)
                .channel(NotificationChannel.PUSH)
                .language("es-CO")
                .rawMessage("Emergencia en km 12.")
                .build();

        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(inv -> {
                    Notification n = inv.getArgument(0);
                    if (n.getId() == null) {
                        n.setId(UUID.randomUUID());
                    }
                    return n;
                });

        when(pushNotifier.sendPush(eq(userId), anyString(), anyString(), any()))
                .thenReturn(PushNotificationResult.failure("SNS Service Unavailable"));

        when(voiceSynthesizer.synthesizeAndUpload(anyString(), anyString()))
                .thenReturn(VoiceSynthesisResult.failure("Polly Access Denied"));

        NotificationResponse response = notificationService.sendDirect(request);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(response.getFailureReason()).contains("SNS Service Unavailable");
        assertThat(response.getFailureReason()).contains("Polly Access Denied");

        verify(pushNotifier, times(1)).sendPush(any(), any(), any(), any());
        verify(voiceSynthesizer, times(1)).synthesizeAndUpload(anyString(), anyString());
        verify(eventPublisher, never()).publishSent(any());
        verify(eventPublisher, times(1)).publishFailed(any());
    }
}
