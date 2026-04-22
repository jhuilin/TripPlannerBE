package com.tripplanner.repository;

import com.tripplanner.entity.Trip;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TripRepository extends JpaRepository<Trip, Long> {

    // List view: eager-load stops + costs to avoid N+1; hotels/restaurants excluded intentionally
    @EntityGraph(attributePaths = {"stops", "stops.cost"})
    List<Trip> findByUserIdOrderByStartDateDesc(Long userId);

    Optional<Trip> findByIdAndUserId(Long id, Long userId);

    // Detail/refine view: eager-loads stops + costs; sub-collections are @BatchSize-loaded on access
    @EntityGraph(attributePaths = {"stops", "stops.cost"})
    Optional<Trip> findWithStopsByIdAndUserId(Long id, Long userId);

    boolean existsByIdAndUserId(Long id, Long userId);

    @Query("SELECT t FROM Trip t WHERE t.confirmed = false AND t.createdAt < :cutoff")
    List<Trip> findUnconfirmedBefore(@Param("cutoff") LocalDateTime cutoff);
}
