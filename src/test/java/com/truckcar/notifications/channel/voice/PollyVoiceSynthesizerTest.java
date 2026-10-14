package com.truckcar.notifications.channel.voice;

import com.truckcar.notifications.model.AudioCache;
import com.truckcar.notifications.repository.AudioCacheRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.polly.PollyClient;
import software.amazon.awssdk.services.polly.model.SynthesizeSpeechRequest;
import software.amazon.awssdk.services.polly.model.SynthesizeSpeechResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URL;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PollyVoiceSynthesizerTest {

    @Mock
    private PollyClient pollyClient;

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private AudioCacheRepository audioCacheRepository;

    @Mock
    private PresignedGetObjectRequest presignedGetObjectRequest;

    private PollyVoiceSynthesizer voiceSynthesizer;

    @BeforeEach
    void setUp() throws Exception {
        voiceSynthesizer = new PollyVoiceSynthesizer(pollyClient, s3Client, s3Presigner, audioCacheRepository);
        ReflectionTestUtils.setField(voiceSynthesizer, "s3BucketName", "test-bucket");
        ReflectionTestUtils.setField(voiceSynthesizer, "defaultVoiceId", "Lupe");
        ReflectionTestUtils.setField(voiceSynthesizer, "presignedUrlDurationHours", 24L);

        when(presignedGetObjectRequest.url()).thenReturn(new URI("https://s3.amazonaws.com/test-bucket/audio.mp3").toURL());
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presignedGetObjectRequest);
    }

    @Test
    @DisplayName("Should reuse cached audio when text hash exists in database")
    void shouldReuseAudioWhenCacheHit() {
        String text = "Alerta vial en La Línea";
        AudioCache cachedEntry = AudioCache.builder()
                .id(UUID.randomUUID())
                .textHash("some-hash")
                .s3Key("audio/es-CO/cached.mp3")
                .voiceId("Lupe")
                .language("es-CO")
                .lastAccessedAt(Instant.now())
                .build();

        when(audioCacheRepository.findByTextHash(anyString())).thenReturn(Optional.of(cachedEntry));

        VoiceSynthesisResult result = voiceSynthesizer.synthesizeAndUpload(text, "es-CO");

        assertThat(result.success()).isTrue();
        assertThat(result.fromCache()).isTrue();
        assertThat(result.s3Key()).isEqualTo("audio/es-CO/cached.mp3");

        // Verify Polly was NOT invoked
        verify(pollyClient, never()).synthesizeSpeech(any(SynthesizeSpeechRequest.class));
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("Should synthesize with Polly and upload to S3 on cache miss")
    void shouldSynthesizeWithPollyWhenCacheMiss() {
        String text = "Nuevo reporte de accidente en km 20";

        when(audioCacheRepository.findByTextHash(anyString())).thenReturn(Optional.empty());

        ResponseInputStream<SynthesizeSpeechResponse> responseStream =
                new ResponseInputStream<>(
                        SynthesizeSpeechResponse.builder().build(),
                        new ByteArrayInputStream("fake-mp3-bytes".getBytes())
                );
        when(pollyClient.synthesizeSpeech(any(SynthesizeSpeechRequest.class))).thenReturn(responseStream);

        VoiceSynthesisResult result = voiceSynthesizer.synthesizeAndUpload(text, "es-CO");

        assertThat(result.success()).isTrue();
        assertThat(result.fromCache()).isFalse();
        assertThat(result.audioUrl()).contains("https://s3.amazonaws.com/test-bucket");

        verify(pollyClient, times(1)).synthesizeSpeech(any(SynthesizeSpeechRequest.class));
        verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        verify(audioCacheRepository, times(1)).save(any(AudioCache.class));
    }
}
