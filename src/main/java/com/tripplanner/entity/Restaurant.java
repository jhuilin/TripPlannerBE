package com.tripplanner.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "restaurants", indexes = {
        @Index(name = "idx_restaurants_stop_id", columnList = "stop_id"),
        @Index(name = "idx_restaurants_stop_id_day_date", columnList = "stop_id, day_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Restaurant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stop_id", nullable = false)
    private TripStop stop;

    private LocalDate dayDate;

    @Column(nullable = false)
    private String name;

    private String cuisine;

    private Integer priceLevel;

    private Double rating;

    private String address;

    private Double lat;
    private Double lng;
}
