package com.tripplanner.controller;

import com.tripplanner.dto.request.PackingItemRequest;
import com.tripplanner.dto.request.PackingRefineRequest;
import com.tripplanner.dto.response.PackingItemResponse;
import com.tripplanner.service.PackingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/trips/{tripId}/packing")
@RequiredArgsConstructor
public class PackingController {

    private final PackingService packingService;

    @GetMapping
    public List<PackingItemResponse> getItems(@PathVariable Long tripId) {
        return packingService.getItems(tripId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PackingItemResponse addItem(@PathVariable Long tripId,
                                       @Valid @RequestBody PackingItemRequest request) {
        return packingService.addItem(tripId, request);
    }

    @PatchMapping("/{itemId}/toggle")
    public PackingItemResponse toggleCheck(@PathVariable Long tripId,
                                           @PathVariable Long itemId) {
        return packingService.toggleCheck(tripId, itemId);
    }

    @DeleteMapping("/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteItem(@PathVariable Long tripId, @PathVariable Long itemId) {
        packingService.deleteItem(tripId, itemId);
    }

    @PostMapping("/refine")
    public List<PackingItemResponse> refine(@PathVariable Long tripId,
                                            @Valid @RequestBody PackingRefineRequest request) {
        return packingService.refinePackingList(tripId, request.refinementRequest());
    }
}
