package com.tripplanner.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripplanner.dto.request.TripStopUpdateRequest;
import com.tripplanner.dto.response.*;
import com.tripplanner.entity.*;
import com.tripplanner.enums.PackingCategory;
import com.tripplanner.enums.TripStatus;
import com.tripplanner.enums.TripStyle;
import com.tripplanner.repository.PackingItemRepository;
import com.tripplanner.repository.TripRepository;
import com.tripplanner.repository.TripStopRepository;
import com.tripplanner.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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
        Trip trip = findOwnedTrip(tripId);
        tripRepository.delete(trip);
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

    private Trip findOwnedTrip(Long tripId) {
        User user = currentUser();
        return tripRepository.findByIdAndUserId(tripId, user.getId())
                .orElseThrow(() -> new EntityNotFoundException("Trip not found"));
    }

    private User currentUser() {
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
                trip.getDepartureCity(),
                trip.getStartDate(),
                trip.getEndDate(),
                trip.getBudget(),
                trip.getCurrency(),
                trip.getStyle(),
                computeStatus(trip),
                estimatedTotal,
                stopResponses
        );
    }

    @Transactional
    public Trip parseAndSaveTrip(User user, String json, Long existingTripId) {
        try {
            JsonNode root = objectMapper.readTree(json);

            Trip trip = existingTripId != null
                    ? tripRepository.findByIdAndUserId(existingTripId, user.getId())
                            .orElseThrow(() -> new EntityNotFoundException("Trip not found"))
                    : new Trip();

            trip.setUser(user);
            trip.setDestination(root.path("destination").asText());
            trip.setDepartureCity(root.path("departureCity").isNull() ? null : root.path("departureCity").asText());
            trip.setStartDate(LocalDate.parse(root.path("startDate").asText()));
            trip.setEndDate(LocalDate.parse(root.path("endDate").asText()));
            trip.setBudget(BigDecimal.valueOf(root.path("budget").asDouble()));
            trip.setCurrency(root.path("currency").asText("USD"));
            trip.setStyle(TripStyle.valueOf(root.path("style").asText("MIXED")));
            trip.setPublic(false);

            Trip savedTrip = tripRepository.save(trip);

            if (existingTripId != null && savedTrip.getStops() != null) {
                savedTrip.getStops().clear();
            }

            List<TripStop> stops = new ArrayList<>();
            for (JsonNode s : root.path("stops")) {
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
                TripStopCost cost = TripStopCost.builder()
                        .stop(savedStop)
                        .intercityTransport(c.path("intercityTransport").isNull() ? null : BigDecimal.valueOf(c.path("intercityTransport").asDouble()))
                        .localTransport(c.path("localTransport").isNull() ? null : BigDecimal.valueOf(c.path("localTransport").asDouble()))
                        .accommodation(c.path("accommodation").isNull() ? null : BigDecimal.valueOf(c.path("accommodation").asDouble()))
                        .food(c.path("food").isNull() ? null : BigDecimal.valueOf(c.path("food").asDouble()))
                        .activities(c.path("activities").isNull() ? null : BigDecimal.valueOf(c.path("activities").asDouble()))
                        .total(BigDecimal.valueOf(c.path("total").asDouble()))
                        .build();
                savedStop.setCost(cost);
                tripStopRepository.save(savedStop);
                stops.add(savedStop);
            }
            savedTrip.setStops(stops);

            packingItemRepository.deleteByTripId(savedTrip.getId());
            for (JsonNode p : root.path("packingItems")) {
                packingItemRepository.save(PackingItem.builder()
                        .trip(savedTrip)
                        .category(PackingCategory.valueOf(p.path("category").asText("ESSENTIALS")))
                        .itemName(p.path("itemName").asText())
                        .quantity(p.path("quantity").asInt(1))
                        .build());
            }

            return tripRepository.findById(savedTrip.getId()).orElse(savedTrip);
        } catch (IllegalArgumentException | EntityNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse itinerary from AI response: " + e.getMessage(), e);
        }
    }

    private TripStopResponse toStopResponse(TripStop stop) {
        TripStopCostResponse costResponse = null;
        if (stop.getCost() != null) {
            TripStopCost c = stop.getCost();
            costResponse = new TripStopCostResponse(
                    c.getIntercityTransport(), c.getLocalTransport(),
                    c.getAccommodation(), c.getFood(), c.getActivities(), c.getTotal());
        }
        return new TripStopResponse(stop.getId(), stop.getLocationName(),
                stop.getLat(), stop.getLng(), stop.getArrivalDate(),
                stop.getDepartureDate(), stop.getOrderIndex(), stop.getSubPlaces(), costResponse);
    }
}
