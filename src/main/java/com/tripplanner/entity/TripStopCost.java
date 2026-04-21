package com.tripplanner.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "trip_stop_costs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TripStopCost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stop_id", nullable = false)
    private TripStop stop;

    @Column(precision = 10, scale = 2)
    private BigDecimal intercityTransport;

    private String intercityTransportType;

    @Column(precision = 10, scale = 2)
    private BigDecimal localTransport;

    @Column(precision = 10, scale = 2)
    private BigDecimal accommodation;

    @Column(precision = 10, scale = 2)
    private BigDecimal food;

    @Column(precision = 10, scale = 2)
    private BigDecimal activities;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total;

    private Integer intercityPublicTransportTimeMins;

    private Integer intercityCarTimeMins;
}
