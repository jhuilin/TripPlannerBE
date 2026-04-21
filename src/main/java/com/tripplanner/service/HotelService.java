package com.tripplanner.service;

import com.tripplanner.dto.request.HotelSelectRequest;
import com.tripplanner.dto.response.HotelResponse;
import com.tripplanner.entity.Hotel;
import com.tripplanner.entity.TripStop;
import com.tripplanner.repository.HotelRepository;
import com.tripplanner.repository.TripRepository;
import com.tripplanner.repository.TripStopRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class HotelService {

    private final HotelRepository hotelRepository;
    private final TripRepository tripRepository;
    private final TripStopRepository tripStopRepository;
    private final OpenAIService openAIService;

    public List<HotelResponse> getRecommendations(Long tripId, Long stopId, String preferences, Long userId) {
        validateOwnership(tripId, userId);
        TripStop stop = getStop(stopId, tripId);
        return openAIService.getHotelRecommendations(stop, preferences);
    }

    @Transactional
    public HotelResponse selectHotel(Long tripId, Long stopId, HotelSelectRequest req, Long userId) {
        validateOwnership(tripId, userId);
        TripStop stop = getStop(stopId, tripId);
        Hotel saved = hotelRepository.save(Hotel.builder()
                .stop(stop)
                .name(req.name())
                .starRating(req.starRating())
                .pricePerNight(req.pricePerNight())
                .address(req.address())
                .lat(req.lat())
                .lng(req.lng())
                .aiSelected(false)
                .build());
        return toResponse(saved);
    }

    @Transactional
    public HotelResponse letAiDecide(Long tripId, Long stopId, String preferences, Long userId) {
        validateOwnership(tripId, userId);
        TripStop stop = getStop(stopId, tripId);
        List<HotelResponse> recs = openAIService.getHotelRecommendations(stop, preferences);
        if (recs.isEmpty()) throw new EntityNotFoundException("No hotel recommendations available");
        HotelResponse top = recs.get(0);
        Hotel saved = hotelRepository.save(Hotel.builder()
                .stop(stop)
                .name(top.name())
                .starRating(top.starRating())
                .pricePerNight(top.pricePerNight())
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

    private HotelResponse toResponse(Hotel h) {
        return new HotelResponse(h.getId(), h.getStop().getId(), h.getName(),
                h.getStarRating(), h.getPricePerNight(), h.getAddress(),
                h.getLat(), h.getLng(), h.isAiSelected());
    }
}
