package com.tripplanner.service;

import com.tripplanner.dto.request.PackingItemRequest;
import com.tripplanner.dto.response.PackingItemResponse;
import com.tripplanner.entity.PackingItem;
import com.tripplanner.entity.Trip;
import com.tripplanner.entity.User;
import com.tripplanner.repository.PackingItemRepository;
import com.tripplanner.repository.TripRepository;
import com.tripplanner.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PackingService {

    private final PackingItemRepository packingItemRepository;
    private final TripRepository tripRepository;
    private final UserRepository userRepository;

    public List<PackingItemResponse> getItems(Long tripId) {
        assertOwnership(tripId);
        return packingItemRepository.findByTripId(tripId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public PackingItemResponse addItem(Long tripId, PackingItemRequest request) {
        Trip trip = findOwnedTrip(tripId);
        PackingItem item = PackingItem.builder()
                .trip(trip)
                .category(request.category())
                .itemName(request.itemName())
                .quantity(request.quantity() != null ? request.quantity() : 1)
                .build();
        return toResponse(packingItemRepository.save(item));
    }

    @Transactional
    public PackingItemResponse toggleCheck(Long tripId, Long itemId) {
        assertOwnership(tripId);
        PackingItem item = packingItemRepository.findById(itemId)
                .orElseThrow(() -> new EntityNotFoundException("Item not found"));
        item.setChecked(!item.isChecked());
        return toResponse(packingItemRepository.save(item));
    }

    @Transactional
    public void deleteItem(Long tripId, Long itemId) {
        assertOwnership(tripId);
        packingItemRepository.deleteById(itemId);
    }

    private Trip findOwnedTrip(Long tripId) {
        User user = currentUser();
        return tripRepository.findByIdAndUserId(tripId, user.getId())
                .orElseThrow(() -> new EntityNotFoundException("Trip not found"));
    }

    private void assertOwnership(Long tripId) {
        findOwnedTrip(tripId);
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
    }

    private PackingItemResponse toResponse(PackingItem item) {
        return new PackingItemResponse(item.getId(), item.getCategory(),
                item.getItemName(), item.getQuantity(), item.isChecked());
    }
}
