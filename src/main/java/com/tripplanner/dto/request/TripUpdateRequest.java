package com.tripplanner.dto.request;

import com.tripplanner.enums.DayIntensity;
import com.tripplanner.enums.TripCategory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record TripUpdateRequest(
        String title,
        String shortDescription,
        LocalDate startDate,
        Integer totalDays,
        BigDecimal budget,
        String currency,
        List<TripCategory> categories,
        DayIntensity intensity,
        String additionalInfo
) {}
