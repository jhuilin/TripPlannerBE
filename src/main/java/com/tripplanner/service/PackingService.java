package com.tripplanner.service;

import com.tripplanner.dto.request.PackingItemRequest;
import com.tripplanner.dto.response.PackingItemResponse;
import com.tripplanner.entity.PackingItem;
import com.tripplanner.entity.Trip;
import com.tripplanner.repository.PackingItemRepository;
import com.tripplanner.repository.TripRepository;
import com.tripplanner.security.CurrentUserResolver;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PackingService {

    private final PackingItemRepository packingItemRepository;
    private final TripRepository tripRepository;
    private final CurrentUserResolver currentUserResolver;
    private final OpenAIService openAIService;

    @Transactional(readOnly = true)
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
        PackingItem item = packingItemRepository.findByIdAndTripId(itemId, tripId)
                .orElseThrow(() -> new EntityNotFoundException("Item not found"));
        item.setChecked(!item.isChecked());
        return toResponse(packingItemRepository.save(item));
    }

    @Transactional
    public void deleteItem(Long tripId, Long itemId) {
        assertOwnership(tripId);
        packingItemRepository.findByIdAndTripId(itemId, tripId)
                .orElseThrow(() -> new EntityNotFoundException("Item not found"));
        packingItemRepository.deleteById(itemId);
    }

    @Transactional
    public List<PackingItemResponse> refinePackingList(Long tripId, String refinementRequest) {
        Trip trip = findOwnedTrip(tripId); // allowed at any time — pre or post confirm
        List<PackingItem> currentItems = packingItemRepository.findByTripId(tripId);
        List<PackingItemResponse> refined = openAIService.refinePackingList(trip, currentItems, refinementRequest);
        packingItemRepository.deleteAll(currentItems);
        List<PackingItem> newItems = refined.stream()
                .map(item -> PackingItem.builder()
                        .trip(trip)
                        .category(item.category())
                        .itemName(item.itemName())
                        .quantity(item.quantity())
                        .build())
                .toList();
        packingItemRepository.saveAll(newItems);
        trip.setPackingRefineCount(trip.getPackingRefineCount() + 1);
        tripRepository.save(trip);
        return packingItemRepository.findByTripId(tripId).stream().map(this::toResponse).toList();
    }

    private Trip findOwnedTrip(Long tripId) {
        Long userId = currentUserResolver.resolveId();
        return tripRepository.findByIdAndUserId(tripId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Trip not found"));
    }

    private void assertOwnership(Long tripId) {
        if (!tripRepository.existsByIdAndUserId(tripId, currentUserResolver.resolveId())) {
            throw new EntityNotFoundException("Trip not found");
        }
    }

    private PackingItemResponse toResponse(PackingItem item) {
        return new PackingItemResponse(item.getId(), item.getCategory(),
                item.getItemName(), item.getQuantity(), item.isChecked());
    }
}
