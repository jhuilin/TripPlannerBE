package com.tripplanner.dto.response;

import java.math.BigDecimal;

public record HotelResponse(
        Long id,
        Long stopId,
        String name,
        Integer starRating,
        BigDecimal pricePerNight,
        String address,
        Double lat,
        Double lng
) {}
