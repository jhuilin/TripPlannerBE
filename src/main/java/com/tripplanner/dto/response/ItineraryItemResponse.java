package com.tripplanner.dto.response;

import com.tripplanner.enums.CommuteMode;
import com.tripplanner.enums.PlaceType;

import java.time.LocalDate;
import java.time.LocalTime;

public record ItineraryItemResponse(
        Long id,
        LocalDate dayDate,
        Integer orderIndex,
        String placeName,
        PlaceType placeType,
        LocalTime startTime,
        Integer durationMins,
        Double distanceFromPrevMiles,
        Integer commuteFromPrevMins,
        CommuteMode commuteMode,
        Double lat,
        Double lng
) {}
