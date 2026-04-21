package com.tripplanner.controller;

import com.tripplanner.dto.request.AiPreferencesRequest;
import com.tripplanner.dto.request.HotelSelectRequest;
import com.tripplanner.dto.response.HotelResponse;
import com.tripplanner.service.HotelService;
import com.tripplanner.service.TripService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/trips/{tripId}/stops/{stopId}/hotels")
@RequiredArgsConstructor
public class HotelController {

    private final HotelService hotelService;
    private final TripService tripService;

    @GetMapping("/recommendations")
    public List<HotelResponse> getRecommendations(
            @PathVariable Long tripId,
            @PathVariable Long stopId) {
        return hotelService.getRecommendations(tripId, stopId, null, tripService.currentUser().getId());
    }

    @PostMapping("/select")
    public HotelResponse selectHotel(
            @PathVariable Long tripId,
            @PathVariable Long stopId,
            @RequestBody HotelSelectRequest request) {
        return hotelService.selectHotel(tripId, stopId, request, tripService.currentUser().getId());
    }

    @PostMapping("/ai-decide")
    public HotelResponse letAiDecide(
            @PathVariable Long tripId,
            @PathVariable Long stopId,
            @RequestBody(required = false) AiPreferencesRequest request) {
        String preferences = request != null ? request.preferences() : null;
        return hotelService.letAiDecide(tripId, stopId, preferences, tripService.currentUser().getId());
    }
}
