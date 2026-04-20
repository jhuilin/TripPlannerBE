package com.tripplanner.repository;

import com.tripplanner.entity.TripStop;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TripStopRepository extends JpaRepository<TripStop, Long> {
    List<TripStop> findByTripIdOrderByOrderIndex(Long tripId);
}
