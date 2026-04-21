package com.tripplanner.dto.response;

import com.tripplanner.enums.DayIntensity;
import com.tripplanner.enums.TripCategory;
import com.tripplanner.enums.TripStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record TripResponse(
        Long id,
        String destination,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal budget,
        String currency,
        List<TripCategory> categories,
        DayIntensity intensity,
        TripStatus status,
        BigDecimal estimatedTotal,
        List<TripStopResponse> stops
) {}
