package com.tripplanner.dto.request;

import com.tripplanner.enums.PackingCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PackingItemRequest(
        @NotNull PackingCategory category,
        @NotBlank String itemName,
        Integer quantity
) {}
