package com.socialwallet.notifications.repository;

import com.socialwallet.notifications.model.DeliveryAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DeliveryAttemptRepository extends JpaRepository<DeliveryAttempt, UUID> {

    List<DeliveryAttempt> findByNotificationIdOrderByAttemptNumberAsc(UUID notificationId);

    int countByNotificationId(UUID notificationId);
}
