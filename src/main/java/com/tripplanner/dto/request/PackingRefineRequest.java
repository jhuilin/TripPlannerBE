package com.tripplanner.dto.request;

import jakarta.validation.constraints.NotBlank;

public record PackingRefineRequest(@NotBlank String refinementRequest) {}
