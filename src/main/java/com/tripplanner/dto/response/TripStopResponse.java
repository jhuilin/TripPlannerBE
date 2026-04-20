package com.tripplanner.dto.response;

import java.time.LocalDate;

public record TripStopResponse(
        Long id,
        String locationName,
        Double lat,
        Double lng,
        LocalDate arrivalDate,
        LocalDate departureDate,
        Integer orderIndex,
        String subPlaces,
        TripStopCostResponse cost
) {}
