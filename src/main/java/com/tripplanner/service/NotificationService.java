package com.tripplanner.service;

import com.tripplanner.dto.response.NotificationResponse;
import com.tripplanner.entity.Notification;
import com.tripplanner.entity.Trip;
import com.tripplanner.entity.User;
import com.tripplanner.repository.NotificationRepository;
import com.tripplanner.repository.TripRepository;
import com.tripplanner.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final TripRepository tripRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<NotificationResponse> getPendingNotifications() {
        User user = currentUser();
        return notificationRepository.findByUserIdAndSentAtIsNull(user.getId())
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public void markAllSent() {
        User user = currentUser();
        notificationRepository.findByUserIdAndSentAtIsNull(user.getId())
                .forEach(n -> {
                    n.setSentAt(LocalDateTime.now());
                    notificationRepository.save(n);
                });
    }

    // Runs daily at 9 AM — schedules notifications for trips starting tomorrow
    @Scheduled(cron = "0 0 9 * * *")
    @Transactional
    public void scheduleUpcomingTripNotifications() {
        LocalDate inThreeDays = LocalDate.now().plusDays(3);
        List<Trip> trips = tripRepository.findTripsStartingOn(inThreeDays);
        for (Trip trip : trips) {
            if (!notificationRepository.existsByTripIdAndSentAtIsNull(trip.getId())) {
                Notification notification = Notification.builder()
                        .user(trip.getUser())
                        .trip(trip)
                        .scheduledDate(inThreeDays)
                        .build();
                notificationRepository.save(notification);
                log.info("Scheduled notification for trip {} starting {}", trip.getId(), inThreeDays);
            }
        }
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
    }

    private NotificationResponse toResponse(Notification n) {
        String firstStop = n.getTrip().getStops() != null && !n.getTrip().getStops().isEmpty()
                ? n.getTrip().getStops().get(0).getLocationName()
                : n.getTrip().getDestination();
        return new NotificationResponse(n.getId(), n.getTrip().getId(),
                n.getTrip().getDestination(), firstStop, n.getScheduledDate());
    }
}
