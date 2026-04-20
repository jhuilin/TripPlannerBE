package com.tripplanner.entity;

import com.tripplanner.enums.PackingCategory;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "packing_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PackingItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PackingCategory category;

    @Column(nullable = false)
    private String itemName;

    @Builder.Default
    private Integer quantity = 1;

    @Builder.Default
    private boolean isChecked = false;
}
