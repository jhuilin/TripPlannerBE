package com.tripplanner.repository;

import com.tripplanner.entity.Trip;
import com.tripplanner.enums.TripStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TripRepository extends JpaRepository<Trip, Long> {

    List<Trip> findByUserIdOrderByStartDateDesc(Long userId);

    Optional<Trip> findByIdAndUserId(Long id, Long userId);

    @Query("SELECT t FROM Trip t WHERE t.startDate = :date")
    List<Trip> findTripsStartingOn(@Param("date") LocalDate date);
}
