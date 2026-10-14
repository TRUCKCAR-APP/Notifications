package com.truckcar.notifications.repository;

import com.truckcar.notifications.model.NotificationCategory;
import com.truckcar.notifications.model.NotificationChannel;
import com.truckcar.notifications.model.NotificationTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, UUID> {

    Optional<NotificationTemplate> findByCategoryAndLanguageAndChannel(
            NotificationCategory category,
            String language,
            NotificationChannel channel
    );

    Optional<NotificationTemplate> findByCategoryAndChannel(
            NotificationCategory category,
            NotificationChannel channel
    );
}
