package com.tripplanner.dto.response;

public record AuthResponse(String token, String refreshToken, String email, String name) {}
