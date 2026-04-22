package com.tripplanner.dto.request;

import jakarta.validation.constraints.NotBlank;

public record TripRefineRequest(@NotBlank String refinementRequest) {}
