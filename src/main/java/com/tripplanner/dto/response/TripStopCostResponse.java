package com.tripplanner.dto.response;

import java.math.BigDecimal;

public record TripStopCostResponse(
        BigDecimal intercityTransport,
        BigDecimal localTransport,
        BigDecimal accommodation,
        BigDecimal food,
        BigDecimal activities,
        BigDecimal total
) {}
