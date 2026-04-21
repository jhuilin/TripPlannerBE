package com.tripplanner.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "hotels", indexes = {
        @Index(name = "idx_hotels_stop_id", columnList = "stop_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Hotel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stop_id", nullable = false)
    private TripStop stop;

    @Column(nullable = false)
    private String name;

    private Integer starRating;

    private BigDecimal pricePerNight;

    private String address;

    private Double lat;
    private Double lng;

    @Builder.Default
    private boolean aiSelected = false;
}
