package com.tripplanner.repository;

import com.tripplanner.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUserIdAndSentAtIsNull(Long userId);
    boolean existsByTripIdAndSentAtIsNull(Long tripId);
}
