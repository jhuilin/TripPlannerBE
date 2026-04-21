package com.tripplanner.dto.request;

public record RestaurantSelectRequest(
        String name,
        String cuisine,
        Integer priceLevel,
        Double rating,
        String address,
        Double lat,
        Double lng
) {}
