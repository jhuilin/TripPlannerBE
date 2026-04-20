package com.tripplanner.dto.request;

import java.time.LocalDate;

public record TripStopUpdateRequest(
        String locationName,
        LocalDate arrivalDate,
        LocalDate departureDate,
        Integer orderIndex
) {}
