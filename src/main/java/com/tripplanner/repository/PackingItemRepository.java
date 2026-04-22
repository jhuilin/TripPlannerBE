package com.tripplanner.repository;

import com.tripplanner.entity.PackingItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PackingItemRepository extends JpaRepository<PackingItem, Long> {
    List<PackingItem> findByTripId(Long tripId);
    Optional<PackingItem> findByIdAndTripId(Long id, Long tripId);
    void deleteByTripId(Long tripId);
}
