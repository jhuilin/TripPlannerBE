package com.tripplanner.repository;

import com.tripplanner.entity.Restaurant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {
    List<Restaurant> findByStopId(Long stopId);
    List<Restaurant> findByStopIdAndDayDate(Long stopId, LocalDate dayDate);
    void deleteByStopId(Long stopId);
}
