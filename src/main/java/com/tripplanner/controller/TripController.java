package com.tripplanner.controller;

import com.tripplanner.dto.request.TripCreateRequest;
import com.tripplanner.dto.request.TripStopUpdateRequest;
import com.tripplanner.dto.response.TripResponse;
import com.tripplanner.entity.User;
import com.tripplanner.service.OpenAIService;
import com.tripplanner.service.TripService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class TripController {

    private final TripService tripService;
    private final OpenAIService openAIService;

    @PostMapping(value = "/generate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter generate(@Valid @RequestBody TripCreateRequest request) {
        User user = tripService.currentUser();
        SseEmitter emitter = new SseEmitter(300_000L);
        Thread.ofVirtual().start(() -> openAIService.generateTripStream(user, request, emitter));
        return emitter;
    }

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
