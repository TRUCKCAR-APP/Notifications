package com.truckcar.notifications.service.impl;

import com.truckcar.notifications.channel.PushNotifier;
import com.truckcar.notifications.channel.VoiceSynthesizer;
import com.truckcar.notifications.channel.push.PushNotificationResult;
import com.truckcar.notifications.channel.voice.VoiceSynthesisResult;
import com.truckcar.notifications.dto.request.DirectNotificationSendRequest;
import com.truckcar.notifications.dto.response.NotificationResponse;
import com.truckcar.notifications.event.model.NotificationFailedEvent;
import com.truckcar.notifications.event.model.NotificationSentEvent;
import com.truckcar.notifications.event.publisher.NotificationEventPublisher;
import com.truckcar.notifications.exception.ResourceNotFoundException;
import com.truckcar.notifications.mapper.NotificationMapper;
import com.truckcar.notifications.model.Notification;
import com.truckcar.notifications.model.NotificationCategory;
import com.truckcar.notifications.model.NotificationChannel;
import com.truckcar.notifications.model.NotificationStatus;
import com.truckcar.notifications.repository.NotificationRepository;
import com.truckcar.notifications.service.NotificationService;
import com.truckcar.notifications.service.NotificationTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationTemplateService templateService;
    private final PushNotifier pushNotifier;
    private final VoiceSynthesizer voiceSynthesizer;
    private final NotificationEventPublisher eventPublisher;
    private final NotificationMapper notificationMapper;

    @Override
    @Transactional
    public NotificationResponse sendDirect(DirectNotificationSendRequest request) {
        String sourceEventId = "direct-" + UUID.randomUUID();
        String sourceEventType = "DIRECT_SEND_API";

        Notification notification = executeDelivery(
                request.getRecipientUserId(),
                request.getCategory(),
                request.getChannel(),
                sourceEventId,
                sourceEventType,
                request.getLanguage(),
                request.getTemplateVariables(),
                request.getRawMessage()
        );

        return notificationMapper.toResponse(notification);
    }

    @Override
    @Transactional
    public void processEventNotification(
            UUID recipientUserId,
            NotificationCategory category,
            NotificationChannel channel,
            String sourceEventId,
            String sourceEventType,
            String language,
            Map<String, Object> templateVariables,
            String rawMessage
    ) {
        // Idempotency check: if already processed for this sourceEventId and channel, skip
        if (notificationRepository.existsBySourceEventIdAndChannel(sourceEventId, channel)) {
            log.warn("Idempotency match: Notification for sourceEventId='{}' and channel='{}' has already been processed. Skipping.",
                    sourceEventId, channel);
            return;
        }

        executeDelivery(
                recipientUserId,
                category,
                channel,
                sourceEventId,
                sourceEventType,
                language,
                templateVariables,
                rawMessage
        );
    }

    private Notification executeDelivery(
            UUID recipientUserId,
            NotificationCategory category,
            NotificationChannel channel,
            String sourceEventId,
            String sourceEventType,
            String language,
            Map<String, Object> templateVariables,
            String rawMessage
    ) {
        // 1. Resolve rendered payload text
        String payloadText;
        if (rawMessage != null && !rawMessage.isBlank()) {
            payloadText = rawMessage;
        } else {
            payloadText = templateService.resolveTemplateText(category, channel, language, templateVariables);
        }

        // 2. Initialize Notification entity
        Notification notification = Notification.builder()
                .recipientUserId(recipientUserId)
                .category(category)
                .channel(channel)
                .sourceEventId(sourceEventId)
                .sourceEventType(sourceEventType)
                .payload(payloadText)
                .status(NotificationStatus.PENDING)
                .attempts(1)
                .build();

        notification = notificationRepository.save(notification);

        // 3. Dispatch by channel
        if (channel == NotificationChannel.PUSH) {
            String title = "TruckCar - " + formatCategoryTitle(category);
            PushNotificationResult pushResult = pushNotifier.sendPush(recipientUserId, title, payloadText, templateVariables);

            if (pushResult.success()) {
                notification.setStatus(NotificationStatus.SENT);
                notification.setSentAt(pushResult.sentAt() != null ? pushResult.sentAt() : Instant.now());
                notificationRepository.save(notification);

                eventPublisher.publishSent(NotificationSentEvent.builder()
                        .notificationId(notification.getId())
                        .recipientUserId(recipientUserId)
                        .channel(channel)
                        .category(category)
                        .sourceEventId(sourceEventId)
                        .sourceEventType(sourceEventType)
                        .sentAt(notification.getSentAt())
                        .build());
            } else {
                notification.setStatus(NotificationStatus.FAILED);
                notification.setFailureReason(pushResult.errorReason());
                notificationRepository.save(notification);

                eventPublisher.publishFailed(NotificationFailedEvent.builder()
                        .notificationId(notification.getId())
                        .recipientUserId(recipientUserId)
                        .channel(channel)
                        .category(category)
                        .sourceEventId(sourceEventId)
                        .sourceEventType(sourceEventType)
                        .failureReason(pushResult.errorReason())
                        .attempts(notification.getAttempts())
                        .failedAt(Instant.now())
                        .build());
            }

        } else if (channel == NotificationChannel.VOICE) {
            VoiceSynthesisResult voiceResult = voiceSynthesizer.synthesizeAndUpload(payloadText, language);

            if (voiceResult.success()) {
                notification.setStatus(NotificationStatus.SENT);
                notification.setAudioUrl(voiceResult.audioUrl());
                notification.setSentAt(Instant.now());
                notificationRepository.save(notification);

                eventPublisher.publishSent(NotificationSentEvent.builder()
                        .notificationId(notification.getId())
                        .recipientUserId(recipientUserId)
                        .channel(channel)
                        .category(category)
                        .sourceEventId(sourceEventId)
                        .sourceEventType(sourceEventType)
                        .audioUrl(voiceResult.audioUrl())
                        .sentAt(notification.getSentAt())
                        .build());
            } else {
                notification.setStatus(NotificationStatus.FAILED);
                notification.setFailureReason(voiceResult.errorReason());
                notificationRepository.save(notification);

                eventPublisher.publishFailed(NotificationFailedEvent.builder()
                        .notificationId(notification.getId())
                        .recipientUserId(recipientUserId)
                        .channel(channel)
                        .category(category)
                        .sourceEventId(sourceEventId)
                        .sourceEventType(sourceEventType)
                        .failureReason(voiceResult.errorReason())
                        .attempts(notification.getAttempts())
                        .failedAt(Instant.now())
                        .build());
            }
        }

        return notification;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationResponse> getNotificationsByUserId(UUID recipientUserId, Pageable pageable) {
        Page<Notification> page = notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(recipientUserId, pageable);
        return page.map(notificationMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationResponse getNotificationById(UUID id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with id: " + id));
        return notificationMapper.toResponse(notification);
    }

    private String formatCategoryTitle(NotificationCategory category) {
        return switch (category) {
            case REASIGNACION_CARGA -> "Reasignación de Carga";
            case ALERTA_RUTA -> "Alerta en Ruta";
            case CIERRE_VIAL -> "Cierre Vial";
            case MENSAJE_DESPACHADOR -> "Mensaje del Despachador";
            case CAMBIO_ESTADO_VIAJE -> "Estado del Viaje";
        };
    }
}
