package com.tripplanner.controller;

import com.tripplanner.dto.request.TripStopUpdateRequest;
import com.tripplanner.dto.response.TripResponse;
import com.tripplanner.service.TripService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class TripController {

    private final TripService tripService;

    @GetMapping
    public List<TripResponse> getMyTrips() {
        return tripService.getMyTrips();
    }

    @GetMapping("/{tripId}")
    public TripResponse getTrip(@PathVariable Long tripId) {
        return tripService.getTrip(tripId);
    }

    @DeleteMapping("/{tripId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTrip(@PathVariable Long tripId) {
        tripService.deleteTrip(tripId);
    }

    @PatchMapping("/{tripId}/stops/{stopId}")
    public TripResponse updateStop(@PathVariable Long tripId,
                                   @PathVariable Long stopId,
                                   @RequestBody TripStopUpdateRequest request) {
        return tripService.updateStop(tripId, stopId, request);
    }

    @DeleteMapping("/{tripId}/stops/{stopId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteStop(@PathVariable Long tripId, @PathVariable Long stopId) {
        tripService.deleteStop(tripId, stopId);
    }
}
