package com.tripplanner.repository;

import com.tripplanner.entity.Hotel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HotelRepository extends JpaRepository<Hotel, Long> {
    List<Hotel> findByStopId(Long stopId);
    void deleteByStopId(Long stopId);
}
