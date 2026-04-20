package com.tripplanner.dto.response;

import java.time.LocalDate;

public record NotificationResponse(
        Long id,
        Long tripId,
        String destination,
        String firstStop,
        LocalDate scheduledDate
) {}
