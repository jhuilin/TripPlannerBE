package com.tripplanner.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "trip_stops")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TripStop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @Column(nullable = false)
    private String locationName;

    private Double lat;
    private Double lng;

    @Column(nullable = false)
    private LocalDate arrivalDate;

    @Column(nullable = false)
    private LocalDate departureDate;

    @Column(nullable = false)
    private Integer orderIndex;

    @OneToOne(mappedBy = "stop", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    private TripStopCost cost;

    @OneToMany(mappedBy = "stop", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @BatchSize(size = 20)
    @Builder.Default
    private List<Hotel> hotels = new ArrayList<>();

    @OneToMany(mappedBy = "stop", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @BatchSize(size = 20)
    @Builder.Default
    private List<Restaurant> restaurants = new ArrayList<>();

    @OneToMany(mappedBy = "stop", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("dayDate ASC, orderIndex ASC")
    @BatchSize(size = 50)
    @Builder.Default
    private List<ItineraryItem> itineraryItems = new ArrayList<>();
}
