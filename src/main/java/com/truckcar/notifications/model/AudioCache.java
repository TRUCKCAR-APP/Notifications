package com.truckcar.notifications.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audio_cache", indexes = {
        @Index(name = "idx_audio_cache_hash", columnList = "text_hash", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AudioCache {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "text_hash", nullable = false, unique = true, length = 64)
    private String textHash;

    @Column(name = "s3_key", nullable = false, length = 255)
    private String s3Key;

    @Column(name = "voice_id", nullable = false, length = 50)
    private String voiceId;

    @Column(name = "language", nullable = false, length = 10)
    private String language;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "last_accessed_at", nullable = false)
    private Instant lastAccessedAt;
}
