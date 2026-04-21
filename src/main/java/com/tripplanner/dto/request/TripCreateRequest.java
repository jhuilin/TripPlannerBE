package com.tripplanner.dto.request;

import com.tripplanner.enums.DayIntensity;
import com.tripplanner.enums.TripCategory;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record TripCreateRequest(
        @NotBlank String destination,
        @NotNull LocalDate startDate,
        @Min(1) @Max(90) int totalDays,
        @NotEmpty @Size(min = 1, max = 3) List<TripCategory> categories,
        @NotNull DayIntensity intensity,
        @NotNull @DecimalMin("0") BigDecimal budget,
        String currency,
        String additionalInfo
) {}
