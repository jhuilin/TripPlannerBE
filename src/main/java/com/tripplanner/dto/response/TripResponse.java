package com.tripplanner.dto.response;

import com.tripplanner.enums.TripStatus;
import com.tripplanner.enums.TripStyle;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record TripResponse(
        Long id,
        String destination,
        String departureCity,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal budget,
        String currency,
        TripStyle style,
        TripStatus status,
        BigDecimal estimatedTotal,
        List<TripStopResponse> stops
) {}
