package com.tripplanner.dto.response;

import com.tripplanner.enums.PackingCategory;

public record PackingItemResponse(
        Long id,
        PackingCategory category,
        String itemName,
        Integer quantity,
        boolean isChecked
) {}
