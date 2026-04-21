package com.tripplanner.service;

import com.tripplanner.dto.request.RestaurantSelectRequest;
import com.tripplanner.dto.response.RestaurantResponse;
import com.tripplanner.entity.Restaurant;
import com.tripplanner.entity.TripStop;
import com.tripplanner.repository.RestaurantRepository;
import com.tripplanner.repository.TripRepository;
import com.tripplanner.repository.TripStopRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;
    private final TripRepository tripRepository;
    private final TripStopRepository tripStopRepository;
    private final OpenAIService openAIService;

    public List<RestaurantResponse> getRecommendations(Long tripId, Long stopId, LocalDate date,
                                                        String preferences, Long userId) {
        validateOwnership(tripId, userId);
        TripStop stop = getStop(stopId, tripId);
        return openAIService.getRestaurantRecommendations(stop, date, preferences);
    }

    @Transactional
    public RestaurantResponse selectRestaurant(Long tripId, Long stopId, LocalDate date,
                                                RestaurantSelectRequest req, Long userId) {
        validateOwnership(tripId, userId);
        TripStop stop = getStop(stopId, tripId);
        Restaurant saved = restaurantRepository.save(Restaurant.builder()
                .stop(stop)
                .dayDate(date)
                .name(req.name())
                .cuisine(req.cuisine())
                .priceLevel(req.priceLevel())
                .rating(req.rating())
                .address(req.address())
                .lat(req.lat())
                .lng(req.lng())
                .aiSelected(false)
                .build());
        return toResponse(saved);
    }

    @Transactional
    public RestaurantResponse letAiDecide(Long tripId, Long stopId, LocalDate date,
                                           String preferences, Long userId) {
        validateOwnership(tripId, userId);
        TripStop stop = getStop(stopId, tripId);
        List<RestaurantResponse> recs = openAIService.getRestaurantRecommendations(stop, date, preferences);
        if (recs.isEmpty()) throw new EntityNotFoundException("No restaurant recommendations available");
        RestaurantResponse top = recs.get(0);
        Restaurant saved = restaurantRepository.save(Restaurant.builder()
                .stop(stop)
                .dayDate(date)
                .name(top.name())
                .cuisine(top.cuisine())
                .priceLevel(top.priceLevel())
                .rating(top.rating())
                .address(top.address())
                .lat(top.lat())
                .lng(top.lng())
                .aiSelected(true)
                .build());
        return toResponse(saved);
    }

    private void validateOwnership(Long tripId, Long userId) {
        tripRepository.findByIdAndUserId(tripId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Trip not found"));
    }

    private TripStop getStop(Long stopId, Long tripId) {
        return tripStopRepository.findById(stopId)
                .filter(s -> s.getTrip().getId().equals(tripId))
                .orElseThrow(() -> new EntityNotFoundException("Stop not found"));
    }

    private RestaurantResponse toResponse(Restaurant r) {
        return new RestaurantResponse(r.getId(), r.getStop().getId(), r.getDayDate(),
                r.getName(), r.getCuisine(), r.getPriceLevel(), r.getRating(),
                r.getAddress(), r.getLat(), r.getLng(), r.isAiSelected());
    }
}
