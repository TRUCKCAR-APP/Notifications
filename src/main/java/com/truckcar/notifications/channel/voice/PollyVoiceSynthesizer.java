package com.truckcar.notifications.channel.voice;

import com.truckcar.notifications.channel.VoiceSynthesizer;
import com.truckcar.notifications.exception.NotificationDeliveryException;
import com.truckcar.notifications.model.AudioCache;
import com.truckcar.notifications.repository.AudioCacheRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.polly.PollyClient;
import software.amazon.awssdk.services.polly.model.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PollyVoiceSynthesizer implements VoiceSynthesizer {

    private final PollyClient pollyClient;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final AudioCacheRepository audioCacheRepository;

    @Value("${aws.s3.bucket:truckcar-voice-notifications}")
    private String s3BucketName;

    @Value("${aws.polly.voice-id:Lupe}")
    private String defaultVoiceId;

    @Value("${aws.polly.presigned-url-duration-hours:24}")
    private long presignedUrlDurationHours;

    @Override
    @Transactional
    @Retry(name = "pollyVoice", fallbackMethod = "fallbackSynthesize")
    @CircuitBreaker(name = "pollyVoice")
    public VoiceSynthesisResult synthesizeAndUpload(String text, String language) {
        if (text == null || text.isBlank()) {
            return VoiceSynthesisResult.failure("Audio text cannot be empty");
        }

        String effectiveLanguage = (language != null && !language.isBlank()) ? language : "es-CO";
        String textHash = computeSha256(effectiveLanguage + ":" + defaultVoiceId + ":" + text.trim());

        // 1. Check audio cache in database
        Optional<AudioCache> cached = audioCacheRepository.findByTextHash(textHash);
        if (cached.isPresent()) {
            AudioCache entry = cached.get();
            log.info("Audio cache HIT for text hash: {}. Reusing S3 key: {}", textHash, entry.getS3Key());
            entry.setLastAccessedAt(Instant.now());
            audioCacheRepository.save(entry);

            String presignedUrl = generatePresignedUrl(entry.getS3Key());
            return VoiceSynthesisResult.ok(presignedUrl, entry.getS3Key(), true);
        }

        log.info("Audio cache MISS for text hash: {}. Synthesizing with AWS Polly (voice: {})", textHash, defaultVoiceId);

        try {
            // 2. Synthesize with AWS Polly
            SynthesizeSpeechRequest synthRequest = SynthesizeSpeechRequest.builder()
                    .text(text)
                    .voiceId(VoiceId.fromValue(defaultVoiceId))
                    .outputFormat(OutputFormat.MP3)
                    .engine(Engine.NEURAL)
                    .languageCode(mapLanguageCode(effectiveLanguage))
                    .build();

            ResponseInputStream<SynthesizeSpeechResponse> synthResponse = pollyClient.synthesizeSpeech(synthRequest);
            byte[] audioBytes = synthResponse.readAllBytes();

            // 3. Upload to S3
            String s3Key = "audio/" + effectiveLanguage + "/" + UUID.randomUUID() + ".mp3";
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(s3BucketName)
                    .key(s3Key)
                    .contentType("audio/mpeg")
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromBytes(audioBytes));
            log.info("Uploaded synthesized audio to S3: bucket={}, key={}", s3BucketName, s3Key);

            // 4. Save to AudioCache
            AudioCache cacheEntity = AudioCache.builder()
                    .textHash(textHash)
                    .s3Key(s3Key)
                    .voiceId(defaultVoiceId)
                    .language(effectiveLanguage)
                    .lastAccessedAt(Instant.now())
                    .build();
            audioCacheRepository.save(cacheEntity);

            // 5. Generate presigned URL
            String presignedUrl = generatePresignedUrl(s3Key);
            return VoiceSynthesisResult.ok(presignedUrl, s3Key, false);

        } catch (Exception e) {
            log.error("Failed to synthesize audio with Polly or upload to S3: {}", e.getMessage(), e);
            throw new NotificationDeliveryException("Polly audio synthesis error: " + e.getMessage(), e);
        }
    }

    public VoiceSynthesisResult fallbackSynthesize(String text, String language, Throwable t) {
        log.error("Fallback triggered for Polly Voice Synthesis. Cause: {}", t.getMessage());
        return VoiceSynthesisResult.failure("Polly synthesis failed after retries: " + t.getMessage());
    }

    public String generatePresignedUrl(String s3Key) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(s3BucketName)
                .key(s3Key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofHours(presignedUrlDurationHours))
                .getObjectRequest(getObjectRequest)
                .build();

        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignRequest);
        return presigned.url().toString();
    }

    private LanguageCode mapLanguageCode(String language) {
        if (language == null) {
            return LanguageCode.ES_US;
        }
        return switch (language.toLowerCase()) {
            case "es-co", "es-419", "es-la" -> LanguageCode.ES_US;
            case "es-es" -> LanguageCode.ES_ES;
            case "en-us" -> LanguageCode.EN_US;
            default -> LanguageCode.ES_US;
        };
    }

    private String computeSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
