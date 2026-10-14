package com.truckcar.notifications.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_templates", uniqueConstraints = {
        @UniqueConstraint(name = "uk_template_cat_lang_chan", columnNames = {"category", "language", "channel"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 50)
    private NotificationCategory category;

    @Column(name = "language", nullable = false, length = 10)
    @Builder.Default
    private String language = "es-CO";

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private NotificationChannel channel;

    @Column(name = "template_text", nullable = false, columnDefinition = "TEXT")
    private String templateText;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
