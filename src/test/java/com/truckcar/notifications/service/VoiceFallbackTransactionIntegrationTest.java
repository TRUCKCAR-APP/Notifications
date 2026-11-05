package com.truckcar.notifications.service;

import com.truckcar.notifications.dto.request.DirectNotificationSendRequest;
import com.truckcar.notifications.dto.response.NotificationResponse;
import com.truckcar.notifications.model.Notification;
import com.truckcar.notifications.model.NotificationCategory;
import com.truckcar.notifications.model.NotificationChannel;
import com.truckcar.notifications.model.NotificationStatus;
import com.truckcar.notifications.repository.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.services.polly.PollyClient;
import software.amazon.awssdk.services.polly.model.PollyException;
import software.amazon.awssdk.services.polly.model.SynthesizeSpeechRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.sns.SnsClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Polly denegado (AWS Academy Learner Lab): el fallback del sintetizador real (Retry + CircuitBreaker) devuelve un
 * fallo y la notificación debe quedar FAILED en la base, sin que la transacción de sendDirect termine en rollback.
 */
@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1)
class VoiceFallbackTransactionIntegrationTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @MockBean
    private PollyClient pollyClient;

    @MockBean
    private SnsClient snsClient;

    @MockBean
    private S3Client s3Client;

    @MockBean
    private S3Presigner s3Presigner;

    @Test
    @DisplayName("Polly denegado: sendDirect responde FAILED y el estado queda guardado")
    void voiceFallbackPersistsFailedStatus() {
        when(pollyClient.synthesizeSpeech(any(SynthesizeSpeechRequest.class)))
                .thenThrow(PollyException.builder().statusCode(403).message("AccessDeniedException").build());

        NotificationResponse response = notificationService.sendDirect(DirectNotificationSendRequest.builder()
                .recipientUserId(UUID.randomUUID())
                .category(NotificationCategory.ALERTA_RUTA)
                .channel(NotificationChannel.VOICE)
                .rawMessage("Derrumbe en la vía al Llano, km 58")
                .build());

        assertThat(response.getStatus()).isEqualTo(NotificationStatus.FAILED);
        Notification stored = notificationRepository.findById(response.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(stored.getFailureReason()).contains("Polly");
    }
}
