package com.tripplanner.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripplanner.dto.request.TripCreateRequest;
import com.tripplanner.dto.request.TripUpdateRequest;
import com.tripplanner.dto.response.*;
import com.tripplanner.entity.*;
import com.tripplanner.enums.CommuteMode;
import com.tripplanner.enums.PackingCategory;
import com.tripplanner.enums.PlaceType;
import com.tripplanner.enums.TripStatus;
import com.tripplanner.repository.*;
import com.tripplanner.security.CurrentUserResolver;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@Slf4j
@Service
@RequiredArgsConstructor
public class TripService {

    private final TripRepository tripRepository;
    private final TripStopRepository tripStopRepository;
    private final PackingItemRepository packingItemRepository;
    private final HotelRepository hotelRepository;
    private final RestaurantRepository restaurantRepository;
    private final ItineraryItemRepository itineraryItemRepository;
    private final CurrentUserResolver currentUserResolver;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<TripResponse> getMyTrips() {
        User user = currentUser();
        // findByUserIdOrderByStartDateDesc uses @EntityGraph to fetch stops+costs — no N+1
        return tripRepository.findByUserIdOrderByStartDateDesc(user.getId())
                .stream().map(this::toListResponse).toList();
    }

    @Transactional(readOnly = true)
    public TripResponse getTrip(Long tripId) {
        User user = currentUser();
        Trip trip = tripRepository.findWithStopsByIdAndUserId(tripId, user.getId())
                .orElseThrow(() -> new EntityNotFoundException("Trip not found"));
        return toResponse(trip);
    }

    @Transactional
    public TripResponse updateTrip(Long tripId, TripUpdateRequest request) {
        Trip trip = findOwnedTrip(tripId);
        if (request.title() != null) trip.setTitle(request.title());
        if (request.shortDescription() != null) trip.setShortDescription(request.shortDescription());
        if (request.startDate() != null || request.totalDays() != null) {
            LocalDate effectiveStart = request.startDate() != null ? request.startDate() : trip.getStartDate();
            int effectiveDays = request.totalDays() != null ? request.totalDays()
                    : (int) (trip.getEndDate().toEpochDay() - trip.getStartDate().toEpochDay()) + 1;
            trip.setStartDate(effectiveStart);
            trip.setEndDate(effectiveStart.plusDays(effectiveDays - 1));
        }
        if (request.budget() != null) trip.setBudget(request.budget());
        if (request.currency() != null) trip.setCurrency(request.currency());
        if (request.categories() != null) trip.setCategories(request.categories());
        if (request.intensity() != null) trip.setIntensity(request.intensity());
        if (request.additionalInfo() != null) trip.setAdditionalInfo(request.additionalInfo());
        return toResponse(tripRepository.save(trip));
    }

    @Transactional
    public void confirmTrip(Long tripId) {
        Trip trip = findOwnedTrip(tripId);
        trip.setConfirmed(true);
        tripRepository.save(trip);
    }

    @Transactional
    public Trip clearAndPrepareForRegenerate(Long tripId, Long userId) {
        Trip trip = findOwnedTrip(tripId, userId);
        if (trip.isConfirmed()) {
            throw new IllegalStateException("Cannot regenerate a confirmed trip — use refine instead");
        }
        if (trip.getStops() != null) trip.getStops().clear();
        if (trip.getPackingItems() != null) trip.getPackingItems().clear();
        trip.setPackingRefineCount(0);
        return tripRepository.saveAndFlush(trip);
    }

    @Transactional
    public void applyRegeneratedContent(Long tripId, Long userId, List<String> stopJsons,
                                        String packingJson, String title, String shortDescription) {
        Trip trip = findOwnedTrip(tripId, userId);
        if (title != null) trip.setTitle(title);
        if (shortDescription != null) trip.setShortDescription(shortDescription);
        trip.setRefineCount(trip.getRefineCount() + 1);
        tripRepository.save(trip);
        saveStopsAndPacking(trip, stopJsons, packingJson);
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupUnconfirmedTrips() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
        List<Trip> stale = tripRepository.findUnconfirmedBefore(cutoff);
        tripRepository.deleteAll(stale);
        if (!stale.isEmpty()) {
            log.info("Deleted {} unconfirmed trips older than 7 days", stale.size());
        }
    }

    @Transactional
    public void deleteTrip(Long tripId) {
        tripRepository.delete(findOwnedTrip(tripId));
    }


    @Transactional
    public Trip parseAndSaveFromStops(User user, TripCreateRequest req, List<String> stopJsons, String packingJson,
                                      String title, String shortDescription) {
        LocalDate endDate = req.startDate().plusDays(req.totalDays() - 1);

        Trip trip = Trip.builder()
                .user(user)
                .destination(req.destination())
                .title(title)
                .shortDescription(shortDescription)
                .additionalInfo(req.additionalInfo())
                .startDate(req.startDate())
                .endDate(endDate)
                .budget(req.budget())
                .currency(req.currency() != null ? req.currency() : "USD")
                .categories(req.categories())
                .intensity(req.intensity())
                .build();
        Trip savedTrip = tripRepository.save(trip);
        saveStopsAndPacking(savedTrip, stopJsons, packingJson);
        return tripRepository.findById(savedTrip.getId()).orElse(savedTrip);
    }

    @Transactional
    public void replaceTrip(Long tripId, Long userId, List<String> stopJsons, String packingJson,
                            String title, String shortDescription) {
        Trip trip = tripRepository.findByIdAndUserId(tripId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Trip not found"));
        if (trip.getStops() != null) trip.getStops().clear();
        if (trip.getPackingItems() != null) trip.getPackingItems().clear();
        if (title != null) trip.setTitle(title);
        if (shortDescription != null) trip.setShortDescription(shortDescription);
        trip.setRefineCount(trip.getRefineCount() + 1);
        tripRepository.saveAndFlush(trip);
        saveStopsAndPacking(trip, stopJsons, packingJson);
    }

    @Transactional(readOnly = true)
    public Trip getTripForRefine(Long tripId, Long userId) {
        Trip trip = tripRepository.findWithStopsByIdAndUserId(tripId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Trip not found"));
        if (trip.isConfirmed()) {
            throw new IllegalStateException("Cannot refine a confirmed trip");
        }
        // Initialize sub-collections within the transaction so they are usable across the boundary.
        // @BatchSize on TripStop merges these into 3 batch queries rather than N per-stop queries.
        if (trip.getStops() != null) {
            trip.getStops().forEach(stop -> {
                Hibernate.initialize(stop.getHotels());
                Hibernate.initialize(stop.getRestaurants());
                Hibernate.initialize(stop.getItineraryItems());
            });
        }
        return trip;
    }

    public TripStatus computeStatus(Trip trip) {
        LocalDate today = LocalDate.now();
        List<TripStop> stops = trip.getStops();
        LocalDate start = (stops != null && !stops.isEmpty()) ? stops.get(0).getArrivalDate() : trip.getStartDate();
        LocalDate end = (stops != null && !stops.isEmpty()) ? stops.get(stops.size() - 1).getDepartureDate() : trip.getEndDate();

        if (today.isBefore(start)) return TripStatus.PLANNED;
        if (!today.isAfter(end)) return TripStatus.ACTIVE;
        return TripStatus.COMPLETED;
    }

    public Integer computeDayOfTrip(Trip trip, TripStatus status) {
        if (status != TripStatus.ACTIVE) return null;
        List<TripStop> stops = trip.getStops();
        LocalDate start = (stops != null && !stops.isEmpty()) ? stops.get(0).getArrivalDate() : trip.getStartDate();
        return (int) (LocalDate.now().toEpochDay() - start.toEpochDay()) + 1;
    }

    public Trip findOwnedTrip(Long tripId) {
        User user = currentUser();
        return tripRepository.findByIdAndUserId(tripId, user.getId())
                .orElseThrow(() -> new EntityNotFoundException("Trip not found"));
    }

    public Trip findOwnedTrip(Long tripId, Long userId) {
        return tripRepository.findByIdAndUserId(tripId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Trip not found"));
    }

    public User currentUser() {
        return currentUserResolver.resolve();
    }

    // Lightweight response for list view — does not load hotels/restaurants per stop
    private TripResponse toListResponse(Trip trip) {
        return buildResponse(trip, this::toListStopResponse);
    }

    public TripResponse toResponse(Trip trip) {
        return buildResponse(trip, this::toStopResponse);
    }

    private TripResponse buildResponse(Trip trip, Function<TripStop, TripStopResponse> stopMapper) {
        List<TripStopResponse> stopResponses = trip.getStops() == null ? List.of() :
                trip.getStops().stream().map(stopMapper).toList();
        BigDecimal estimatedTotal = stopResponses.stream()
                .map(s -> s.cost() != null ? s.cost().total() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        TripStatus status = computeStatus(trip);
        int totalDays = (int) (trip.getEndDate().toEpochDay() - trip.getStartDate().toEpochDay()) + 1;
        return new TripResponse(trip.getId(), trip.getDestination(), trip.getTitle(),
                trip.getShortDescription(), trip.getStartDate(),
                trip.getEndDate(), totalDays, trip.getBudget(), trip.getCurrency(), trip.getCategories(),
                trip.getIntensity(), status, computeDayOfTrip(trip, status), estimatedTotal,
                stopResponses, trip.isConfirmed(), trip.getRefineCount(), trip.getPackingRefineCount());
    }

    private void saveStopsAndPacking(Trip savedTrip, List<String> stopJsons, String packingJson) {
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

                JsonNode h = s.path("hotel");
                if (!h.isMissingNode() && !h.isNull()) {
                    hotelRepository.save(Hotel.builder()
                            .stop(savedStop)
                            .name(h.path("name").asText())
                            .starRating(h.path("starRating").isNull() ? null : h.path("starRating").asInt())
                            .pricePerNight(h.path("pricePerNight").isNull() ? null : BigDecimal.valueOf(h.path("pricePerNight").asDouble()))
                            .address(h.path("address").asText(""))
                            .lat(h.path("lat").isNull() ? null : h.path("lat").asDouble())
                            .lng(h.path("lng").isNull() ? null : h.path("lng").asDouble())
                            .build());
                }

                JsonNode rests = s.path("restaurants");
                if (rests.isArray()) {
                    for (JsonNode r : rests) {
                        try {
                            restaurantRepository.save(Restaurant.builder()
                                    .stop(savedStop)
                                    .dayDate(LocalDate.parse(r.path("dayDate").asText()))
                                    .name(r.path("name").asText())
                                    .cuisine(r.path("cuisine").asText(""))
                                    .priceLevel(r.path("priceLevel").isNull() ? null : r.path("priceLevel").asInt())
                                    .rating(r.path("rating").isNull() ? null : r.path("rating").asDouble())
                                    .address(r.path("address").asText(""))
                                    .lat(r.path("lat").isNull() ? null : r.path("lat").asDouble())
                                    .lng(r.path("lng").isNull() ? null : r.path("lng").asDouble())
                                    .build());
                        } catch (Exception e) {
                            log.warn("Failed to parse restaurant for stop {}: {}", savedStop.getId(), e.getMessage());
                        }
                    }
                }

                JsonNode items = s.path("itinerary");
                if (items.isArray()) {
                    for (JsonNode item : items) {
                        try {
                            String startTimeStr = item.path("startTime").asText(null);
                            itineraryItemRepository.save(ItineraryItem.builder()
                                    .stop(savedStop)
                                    .dayDate(LocalDate.parse(item.path("dayDate").asText()))
                                    .orderIndex(item.path("orderIndex").asInt())
                                    .placeName(item.path("placeName").asText())
                                    .placeType(PlaceType.fromString(item.path("placeType").asText(null)))
                                    .startTime(startTimeStr != null ? LocalTime.parse(startTimeStr) : null)
                                    .durationMins(intOrNull(item, "durationMins"))
                                    .distanceFromPrevMiles(item.path("distanceFromPrevMiles").isNull() ? null : item.path("distanceFromPrevMiles").asDouble())
                                    .commuteFromPrevMins(intOrNull(item, "commuteFromPrevMins"))
                                    .commuteMode(CommuteMode.fromString(item.path("commuteMode").asText(null)))
                                    .lat(item.path("lat").isNull() ? null : item.path("lat").asDouble())
                                    .lng(item.path("lng").isNull() ? null : item.path("lng").asDouble())
                                    .build());
                        } catch (Exception e) {
                            log.warn("Failed to parse itinerary item for stop {}: {}", savedStop.getId(), e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to parse stop JSON for trip {}, skipping stop: {}", savedTrip.getId(), e.getMessage());
            }
        }

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
    }

    private TripStopResponse toStopResponse(TripStop stop) {
        HotelResponse hotelResponse = null;
        List<Hotel> hotels = stop.getHotels();
        if (hotels != null && !hotels.isEmpty()) {
            Hotel h = hotels.get(0);
            hotelResponse = new HotelResponse(h.getId(), stop.getId(), h.getName(),
                    h.getStarRating(), h.getPricePerNight(), h.getAddress(), h.getLat(), h.getLng());
        }

        List<RestaurantResponse> restaurantResponses = stop.getRestaurants() == null ? List.of() :
                stop.getRestaurants().stream()
                        .map(r -> new RestaurantResponse(r.getId(), stop.getId(), r.getDayDate(), r.getName(),
                                r.getCuisine(), r.getPriceLevel(), r.getRating(), r.getAddress(),
                                r.getLat(), r.getLng()))
                        .toList();

        List<ItineraryItemResponse> itineraryResponses = stop.getItineraryItems() == null ? List.of() :
                stop.getItineraryItems().stream()
                        .map(i -> new ItineraryItemResponse(i.getId(), i.getDayDate(), i.getOrderIndex(),
                                i.getPlaceName(), i.getPlaceType(), i.getStartTime(), i.getDurationMins(),
                                i.getDistanceFromPrevMiles(), i.getCommuteFromPrevMins(), i.getCommuteMode(),
                                i.getLat(), i.getLng()))
                        .toList();

        return new TripStopResponse(stop.getId(), stop.getLocationName(),
                stop.getLat(), stop.getLng(), stop.getArrivalDate(),
                stop.getDepartureDate(), stop.getOrderIndex(),
                toCostResponse(stop), hotelResponse, restaurantResponses, itineraryResponses);
    }

    private TripStopResponse toListStopResponse(TripStop stop) {
        return new TripStopResponse(stop.getId(), stop.getLocationName(),
                stop.getLat(), stop.getLng(), stop.getArrivalDate(),
                stop.getDepartureDate(), stop.getOrderIndex(),
                toCostResponse(stop), null, List.of(), List.of());
    }

    private TripStopCostResponse toCostResponse(TripStop stop) {
        if (stop.getCost() == null) return null;
        TripStopCost c = stop.getCost();
        return new TripStopCostResponse(
                c.getIntercityTransport(), c.getIntercityTransportType(),
                c.getLocalTransport(), c.getAccommodation(),
                c.getFood(), c.getActivities(), c.getTotal(),
                c.getIntercityPublicTransportTimeMins(), c.getIntercityCarTimeMins());
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
