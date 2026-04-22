package com.tripplanner.controller;

import com.tripplanner.dto.request.TripCreateRequest;
import com.tripplanner.dto.request.TripRefineRequest;
import com.tripplanner.dto.request.TripUpdateRequest;
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
import java.util.function.Consumer;

@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class TripController {

    private static final long SSE_TIMEOUT_MS = 300_000L;

    private final TripService tripService;
    private final OpenAIService openAIService;

    @PostMapping(value = "/generate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter generate(@Valid @RequestBody TripCreateRequest request) {
        User user = tripService.currentUser();
        return startSseStream(emitter -> openAIService.generateTripStream(user, request, emitter));
    }

    @PostMapping(value = "/{tripId}/refine", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter refine(@PathVariable Long tripId,
                             @Valid @RequestBody TripRefineRequest request) {
        User user = tripService.currentUser();
        return startSseStream(emitter ->
                openAIService.refineTripStream(user, tripId, request.refinementRequest(), emitter));
    }

    @GetMapping
    public List<TripResponse> getMyTrips() {
        return tripService.getMyTrips();
    }

    @GetMapping("/{tripId}")
    public TripResponse getTrip(@PathVariable Long tripId) {
        return tripService.getTrip(tripId);
    }

    @PatchMapping("/{tripId}")
    public TripResponse updateTrip(@PathVariable Long tripId,
                                   @RequestBody TripUpdateRequest request) {
        return tripService.updateTrip(tripId, request);
    }

    @PostMapping("/{tripId}/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmTrip(@PathVariable Long tripId) {
        tripService.confirmTrip(tripId);
    }

    @PostMapping(value = "/{tripId}/regenerate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter regenerate(@PathVariable Long tripId) {
        User user = tripService.currentUser();
        return startSseStream(emitter -> openAIService.regenerateTripStream(user, tripId, emitter));
    }

    private SseEmitter startSseStream(Consumer<SseEmitter> task) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        Thread.ofVirtual().start(() -> task.accept(emitter));
        return emitter;
    }

    @DeleteMapping("/{tripId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTrip(@PathVariable Long tripId) {
        tripService.deleteTrip(tripId);
    }


}
