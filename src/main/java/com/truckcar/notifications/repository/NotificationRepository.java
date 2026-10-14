package com.truckcar.notifications.repository;

import com.truckcar.notifications.model.Notification;
import com.truckcar.notifications.model.NotificationChannel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    boolean existsBySourceEventIdAndChannel(String sourceEventId, NotificationChannel channel);

    Optional<Notification> findBySourceEventIdAndChannel(String sourceEventId, NotificationChannel channel);

    Page<Notification> findByRecipientUserIdOrderByCreatedAtDesc(UUID recipientUserId, Pageable pageable);
}
