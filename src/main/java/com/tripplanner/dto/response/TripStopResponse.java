package com.tripplanner.dto.response;

import java.time.LocalDate;
import java.util.List;

public record TripStopResponse(
        Long id,
        String locationName,
        Double lat,
        Double lng,
        LocalDate arrivalDate,
        LocalDate departureDate,
        Integer orderIndex,
        TripStopCostResponse cost,
        HotelResponse hotel,
        List<RestaurantResponse> restaurants,
        List<ItineraryItemResponse> itinerary
) {}
