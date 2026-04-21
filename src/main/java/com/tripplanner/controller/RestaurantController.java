package com.tripplanner.controller;

import com.tripplanner.dto.request.AiPreferencesRequest;
import com.tripplanner.dto.request.RestaurantSelectRequest;
import com.tripplanner.dto.response.RestaurantResponse;
import com.tripplanner.service.RestaurantService;
import com.tripplanner.service.TripService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/trips/{tripId}/stops/{stopId}/restaurants")
@RequiredArgsConstructor
public class RestaurantController {

    private final RestaurantService restaurantService;
    private final TripService tripService;

    @GetMapping("/recommendations")
    public List<RestaurantResponse> getRecommendations(
            @PathVariable Long tripId,
            @PathVariable Long stopId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return restaurantService.getRecommendations(tripId, stopId, date, null, tripService.currentUser().getId());
    }

    @PostMapping("/select")
    public RestaurantResponse selectRestaurant(
            @PathVariable Long tripId,
            @PathVariable Long stopId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestBody RestaurantSelectRequest request) {
        return restaurantService.selectRestaurant(tripId, stopId, date, request, tripService.currentUser().getId());
    }

    @PostMapping("/ai-decide")
    public RestaurantResponse letAiDecide(
            @PathVariable Long tripId,
            @PathVariable Long stopId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestBody(required = false) AiPreferencesRequest request) {
        String preferences = request != null ? request.preferences() : null;
        return restaurantService.letAiDecide(tripId, stopId, date, preferences, tripService.currentUser().getId());
    }
}
