package com.tripplanner.controller;

import com.tripplanner.dto.response.*;
import com.tripplanner.enums.*;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api/trips/sample")
public class SampleTripController {

    @GetMapping
    public SampleTripResponse getSample() {
        LocalDate today = LocalDate.now().plusDays(30);

        List<ItineraryItemResponse> day1 = List.of(
                new ItineraryItemResponse(null, today, 0, "Park Hyatt Tokyo", PlaceType.ACCOMMODATION,
                        LocalTime.of(14, 0), 60, 0.0, 0, CommuteMode.NONE, 35.6858, 139.6917),
                new ItineraryItemResponse(null, today, 1, "Shinjuku Gyoen", PlaceType.ATTRACTION,
                        LocalTime.of(15, 30), 90, 1.2, 12, CommuteMode.WALK, 35.6851, 139.7100),
                new ItineraryItemResponse(null, today, 2, "Ichiran Ramen Shinjuku", PlaceType.RESTAURANT,
                        LocalTime.of(19, 0), 60, 0.8, 10, CommuteMode.WALK, 35.6921, 139.7006)
        );

        List<ItineraryItemResponse> day2 = List.of(
                new ItineraryItemResponse(null, today.plusDays(1), 0, "Park Hyatt Tokyo", PlaceType.ACCOMMODATION,
                        LocalTime.of(9, 0), 30, 0.0, 0, CommuteMode.NONE, 35.6858, 139.6917),
                new ItineraryItemResponse(null, today.plusDays(1), 1, "Shibuya Crossing", PlaceType.ATTRACTION,
                        LocalTime.of(10, 0), 60, 2.1, 18, CommuteMode.SUBWAY, 35.6595, 139.7004),
                new ItineraryItemResponse(null, today.plusDays(1), 2, "Gonpachi Nishi-Azabu", PlaceType.RESTAURANT,
                        LocalTime.of(13, 0), 75, 1.4, 14, CommuteMode.TAXI, 35.6627, 139.7239),
                new ItineraryItemResponse(null, today.plusDays(1), 3, "Meiji Shrine", PlaceType.ATTRACTION,
                        LocalTime.of(15, 30), 90, 1.8, 16, CommuteMode.WALK, 35.6763, 139.6993)
        );

        HotelResponse tokyoHotel = new HotelResponse(null, null, "Park Hyatt Tokyo",
                5, new BigDecimal("400.00"), "3-7-1-2 Nishi-Shinjuku, Tokyo", 35.6858, 139.6917, true);

        List<RestaurantResponse> tokyoRestaurants = List.of(
                new RestaurantResponse(null, null, today, "Ichiran Ramen Shinjuku",
                        "Japanese Ramen", 1, 4.5, "Shinjuku, Tokyo", 35.6921, 139.7006, true),
                new RestaurantResponse(null, null, today.plusDays(1), "Gonpachi Nishi-Azabu",
                        "Japanese Izakaya", 3, 4.7, "Nishi-Azabu, Tokyo", 35.6627, 139.7239, true)
        );

        TripStopCostResponse tokyoCost = new TripStopCostResponse(
                new BigDecimal("0"), "none", new BigDecimal("60"),
                new BigDecimal("800"), new BigDecimal("120"), new BigDecimal("80"),
                new BigDecimal("1060"), 0, 0);

        TripStopResponse tokyoStop = new TripStopResponse(null, "Tokyo, Japan",
                35.6762, 139.6503, today, today.plusDays(1),
                0, tokyoCost, tokyoHotel, tokyoRestaurants,
                List.of(day1, day2).stream().flatMap(List::stream).toList());

        List<ItineraryItemResponse> kyotoDay1 = List.of(
                new ItineraryItemResponse(null, today.plusDays(2), 0, "The Thousand Kyoto", PlaceType.ACCOMMODATION,
                        LocalTime.of(15, 0), 60, 0.0, 0, CommuteMode.NONE, 34.9945, 135.7583),
                new ItineraryItemResponse(null, today.plusDays(2), 1, "Fushimi Inari Shrine", PlaceType.ATTRACTION,
                        LocalTime.of(16, 30), 90, 3.5, 20, CommuteMode.TAXI, 34.9671, 135.7727),
                new ItineraryItemResponse(null, today.plusDays(2), 2, "Nishiki Market", PlaceType.ATTRACTION,
                        LocalTime.of(19, 0), 60, 4.2, 22, CommuteMode.SUBWAY, 35.0047, 135.7651)
        );

        HotelResponse kyotoHotel = new HotelResponse(null, null, "The Thousand Kyoto",
                5, new BigDecimal("320.00"), "Shijo-Karasuma, Kyoto", 34.9945, 135.7583, true);

        List<RestaurantResponse> kyotoRestaurants = List.of(
                new RestaurantResponse(null, null, today.plusDays(2), "Kikunoi Honten",
                        "Kaiseki", 3, 4.9, "Higashiyama, Kyoto", 34.9942, 135.7813, true)
        );

        TripStopCostResponse kyotoCost = new TripStopCostResponse(
                new BigDecimal("80"), "train", new BigDecimal("30"),
                new BigDecimal("320"), new BigDecimal("80"), new BigDecimal("60"),
                new BigDecimal("570"), 75, 45);

        TripStopResponse kyotoStop = new TripStopResponse(null, "Kyoto, Japan",
                35.0116, 135.7681, today.plusDays(2), today.plusDays(4),
                1, kyotoCost, kyotoHotel, kyotoRestaurants, kyotoDay1);

        TripResponse trip = new TripResponse(
                null, "Japan", "Cherry Blossoms & Ancient Temples",
                "5 days through Tokyo's electric streets and Kyoto's timeless shrines, with the best ramen and kaiseki along the way.",
                today, today.plusDays(4), 5,
                new BigDecimal("3000"), "USD",
                List.of(TripCategory.LOCAL_CULTURE, TripCategory.SHOPPING),
                DayIntensity.BALANCED, TripStatus.PLANNED, null,
                new BigDecimal("1630"), List.of(tokyoStop, kyotoStop),
                false, 0, 0);

        List<PackingItemResponse> packing = List.of(
                new PackingItemResponse(null, PackingCategory.CLOTHING, "T-shirts", 5, false),
                new PackingItemResponse(null, PackingCategory.CLOTHING, "Comfortable walking shoes", 1, false),
                new PackingItemResponse(null, PackingCategory.ESSENTIALS, "Passport", 1, false),
                new PackingItemResponse(null, PackingCategory.ESSENTIALS, "IC Card (Suica)", 1, false),
                new PackingItemResponse(null, PackingCategory.TOILETRIES, "Sunscreen", 1, false),
                new PackingItemResponse(null, PackingCategory.DESTINATION_SPECIFIC, "Pocket WiFi", 1, false)
        );

        return new SampleTripResponse(trip, packing);
    }

    public record SampleTripResponse(TripResponse trip, List<PackingItemResponse> packingItems) {}
}
