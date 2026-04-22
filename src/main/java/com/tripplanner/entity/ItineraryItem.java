package com.tripplanner.entity;

import com.tripplanner.enums.CommuteMode;
import com.tripplanner.enums.PlaceType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "itinerary_items", indexes = {
        @Index(name = "idx_itinerary_stop_id", columnList = "stop_id"),
        @Index(name = "idx_itinerary_stop_day", columnList = "stop_id, day_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItineraryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stop_id", nullable = false)
    private TripStop stop;

    @Column(nullable = false)
    private LocalDate dayDate;

    @Column(nullable = false)
    private Integer orderIndex;

    @Column(nullable = false)
    private String placeName;

    @Enumerated(EnumType.STRING)
    private PlaceType placeType;

    private LocalTime startTime;

    private Integer durationMins;

    private Double distanceFromPrevMiles;

    private Integer commuteFromPrevMins;

    @Enumerated(EnumType.STRING)
    private CommuteMode commuteMode;

    private Double lat;
    private Double lng;
}
