package com.tripplanner.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ChatMessageRequest(
        Long tripId,
        @NotBlank String message
) {}
