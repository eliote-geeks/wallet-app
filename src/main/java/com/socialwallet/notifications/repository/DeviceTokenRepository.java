package com.socialwallet.notifications.repository;

import com.socialwallet.notifications.model.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, UUID> {

    List<DeviceToken> findByUserIdAndActiveTrue(UUID userId);

    List<DeviceToken> findByUserId(UUID userId);

    Optional<DeviceToken> findByTokenAndActiveTrue(String token);

    long countByUserIdAndActiveTrue(UUID userId);

    @Query("SELECT dt FROM DeviceToken dt WHERE dt.userId = :userId AND dt.active = true ORDER BY dt.lastUsedAt ASC")
    List<DeviceToken> findOldestActiveByUser(@Param("userId") UUID userId);

    @Modifying
    @Query("UPDATE DeviceToken dt SET dt.active = false WHERE dt.active = true AND dt.lastUsedAt < :cutoff")
    int deactivateUnusedTokens(@Param("cutoff") Instant cutoff);
}
