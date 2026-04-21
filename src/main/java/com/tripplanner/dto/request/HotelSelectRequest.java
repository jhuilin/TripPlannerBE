package com.tripplanner.dto.request;

import java.math.BigDecimal;

public record HotelSelectRequest(
        String name,
        Integer starRating,
        BigDecimal pricePerNight,
        String address,
        Double lat,
        Double lng
) {}
