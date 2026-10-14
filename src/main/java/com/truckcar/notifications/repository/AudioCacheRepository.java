package com.truckcar.notifications.repository;

import com.truckcar.notifications.model.AudioCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AudioCacheRepository extends JpaRepository<AudioCache, UUID> {

    Optional<AudioCache> findByTextHash(String textHash);
}
