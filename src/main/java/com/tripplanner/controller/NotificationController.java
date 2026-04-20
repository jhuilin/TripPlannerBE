package com.tripplanner.controller;

import com.tripplanner.dto.response.NotificationResponse;
import com.tripplanner.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public List<NotificationResponse> getPending() {
        return notificationService.getPendingNotifications();
    }

    @PostMapping("/mark-sent")
    public void markAllSent() {
        notificationService.markAllSent();
    }
}
