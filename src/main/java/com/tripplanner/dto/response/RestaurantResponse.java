package com.tripplanner.dto.response;

import java.time.LocalDate;

public record RestaurantResponse(
        Long id,
        Long stopId,
        LocalDate dayDate,
        String name,
        String cuisine,
        Integer priceLevel,
        Double rating,
        String address,
        Double lat,
        Double lng,
        boolean aiSelected
) {}
