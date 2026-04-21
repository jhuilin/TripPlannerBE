package com.tripplanner.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripplanner.dto.request.TripCreateRequest;
import com.tripplanner.dto.request.TripStopUpdateRequest;
import com.tripplanner.dto.response.*;
import com.tripplanner.entity.*;
import com.tripplanner.enums.PackingCategory;
import com.tripplanner.enums.TripStatus;
import com.tripplanner.repository.PackingItemRepository;
import com.tripplanner.repository.TripRepository;
import com.tripplanner.repository.TripStopRepository;
import com.tripplanner.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TripService {

    private final TripRepository tripRepository;
    private final TripStopRepository tripStopRepository;
    private final PackingItemRepository packingItemRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<TripResponse> getMyTrips() {
        User user = currentUser();
        return tripRepository.findByUserIdOrderByStartDateDesc(user.getId())
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public TripResponse getTrip(Long tripId) {
        return toResponse(findOwnedTrip(tripId));
    }

    @Transactional
    public void deleteTrip(Long tripId) {
        tripRepository.delete(findOwnedTrip(tripId));
    }

    @Transactional
    public TripResponse updateStop(Long tripId, Long stopId, TripStopUpdateRequest request) {
        Trip trip = findOwnedTrip(tripId);
        TripStop stop = trip.getStops().stream()
                .filter(s -> s.getId().equals(stopId))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Stop not found"));

        if (request.locationName() != null) stop.setLocationName(request.locationName());
        if (request.arrivalDate() != null) stop.setArrivalDate(request.arrivalDate());
        if (request.departureDate() != null) stop.setDepartureDate(request.departureDate());
        if (request.orderIndex() != null) stop.setOrderIndex(request.orderIndex());

        tripStopRepository.save(stop);
        return toResponse(tripRepository.findById(tripId).orElseThrow());
    }

    @Transactional
    public void deleteStop(Long tripId, Long stopId) {
        Trip trip = findOwnedTrip(tripId);
        trip.getStops().removeIf(s -> s.getId().equals(stopId));
        tripRepository.save(trip);
    }

    @Transactional
    public Trip parseAndSaveFromStops(User user, TripCreateRequest req, List<String> stopJsons, String packingJson) {
        LocalDate endDate = req.startDate().plusDays(req.totalDays() - 1);

        Trip trip = Trip.builder()
                .user(user)
                .destination(req.destination())
                .startDate(req.startDate())
                .endDate(endDate)
                .budget(req.budget())
                .currency(req.currency() != null ? req.currency() : "USD")
                .categories(req.categories())
                .intensity(req.intensity())
                .build();
        Trip savedTrip = tripRepository.save(trip);

        List<TripStop> stops = new ArrayList<>();
        for (String stopJson : stopJsons) {
            try {
                JsonNode s = objectMapper.readTree(stopJson);
                TripStop stop = TripStop.builder()
                        .trip(savedTrip)
                        .locationName(s.path("locationName").asText())
                        .lat(s.path("lat").isNull() ? null : s.path("lat").asDouble())
                        .lng(s.path("lng").isNull() ? null : s.path("lng").asDouble())
                        .arrivalDate(LocalDate.parse(s.path("arrivalDate").asText()))
                        .departureDate(LocalDate.parse(s.path("departureDate").asText()))
                        .orderIndex(s.path("orderIndex").asInt())
                        .subPlaces(s.path("subPlaces").asText(""))
                        .build();
                TripStop savedStop = tripStopRepository.save(stop);

                JsonNode c = s.path("cost");
                if (!c.isMissingNode()) {
                    TripStopCost cost = TripStopCost.builder()
                            .stop(savedStop)
                            .intercityTransport(decimalOrNull(c, "intercityTransport"))
                            .intercityTransportType(c.path("intercityTransportType").asText(null))
                            .localTransport(decimalOrNull(c, "localTransport"))
                            .accommodation(decimalOrNull(c, "accommodation"))
                            .food(decimalOrNull(c, "food"))
                            .activities(decimalOrNull(c, "activities"))
                            .total(BigDecimal.valueOf(c.path("total").asDouble()))
                            .intercityPublicTransportTimeMins(intOrNull(c, "intercityPublicTransportTimeMins"))
                            .intercityCarTimeMins(intOrNull(c, "intercityCarTimeMins"))
                            .build();
                    savedStop.setCost(cost);
                    tripStopRepository.save(savedStop);
                }
                stops.add(savedStop);
            } catch (Exception e) {
                log.warn("Failed to parse stop JSON for trip {}, skipping stop: {}", savedTrip.getId(), e.getMessage());
            }
        }
        savedTrip.setStops(stops);

        if (packingJson != null && !packingJson.isBlank()) {
            try {
                JsonNode root = objectMapper.readTree(packingJson);
                for (JsonNode p : root.path("packingItems")) {
                    packingItemRepository.save(PackingItem.builder()
                            .trip(savedTrip)
                            .category(PackingCategory.valueOf(p.path("category").asText("ESSENTIALS")))
                            .itemName(p.path("itemName").asText())
                            .quantity(p.path("quantity").asInt(1))
                            .build());
                }
            } catch (Exception e) {
                log.warn("Failed to parse packing items for trip {}: {}", savedTrip.getId(), e.getMessage());
            }
        }

        return tripRepository.findById(savedTrip.getId()).orElse(savedTrip);
    }

    public TripStatus computeStatus(Trip trip) {
        LocalDate today = LocalDate.now();
        List<TripStop> stops = trip.getStops();
        if (stops == null || stops.isEmpty()) {
            return today.isBefore(trip.getStartDate()) ? TripStatus.PLANNED : TripStatus.COMPLETED;
        }
        TripStop first = stops.get(0);
        TripStop last = stops.get(stops.size() - 1);

        if (today.isBefore(first.getArrivalDate())) return TripStatus.PLANNED;
        if (!today.isAfter(first.getDepartureDate())) return TripStatus.STARTED;
        if (!today.isAfter(last.getDepartureDate())) return TripStatus.ONGOING;
        return TripStatus.COMPLETED;
    }

    public Trip findOwnedTrip(Long tripId) {
        User user = currentUser();
        return tripRepository.findByIdAndUserId(tripId, user.getId())
                .orElseThrow(() -> new EntityNotFoundException("Trip not found"));
    }

    public User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
    }

    public TripResponse toResponse(Trip trip) {
        List<TripStopResponse> stopResponses = trip.getStops() == null ? List.of() :
                trip.getStops().stream().map(this::toStopResponse).toList();

        BigDecimal estimatedTotal = stopResponses.stream()
                .map(s -> s.cost() != null ? s.cost().total() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new TripResponse(
                trip.getId(),
                trip.getDestination(),
                trip.getStartDate(),
                trip.getEndDate(),
                trip.getBudget(),
                trip.getCurrency(),
                trip.getCategories(),
                trip.getIntensity(),
                computeStatus(trip),
                estimatedTotal,
                stopResponses);
    }

    private TripStopResponse toStopResponse(TripStop stop) {
        TripStopCostResponse costResponse = null;
        if (stop.getCost() != null) {
            TripStopCost c = stop.getCost();
            costResponse = new TripStopCostResponse(
                    c.getIntercityTransport(), c.getIntercityTransportType(),
                    c.getLocalTransport(), c.getAccommodation(),
                    c.getFood(), c.getActivities(), c.getTotal(),
                    c.getIntercityPublicTransportTimeMins(), c.getIntercityCarTimeMins());
        }
        return new TripStopResponse(stop.getId(), stop.getLocationName(),
                stop.getLat(), stop.getLng(), stop.getArrivalDate(),
                stop.getDepartureDate(), stop.getOrderIndex(), stop.getSubPlaces(), costResponse);
    }

    private BigDecimal decimalOrNull(JsonNode node, String field) {
        JsonNode val = node.path(field);
        return val.isNull() || val.isMissingNode() ? null : BigDecimal.valueOf(val.asDouble());
    }

    private Integer intOrNull(JsonNode node, String field) {
        JsonNode val = node.path(field);
        return val.isNull() || val.isMissingNode() ? null : val.asInt();
    }
}
