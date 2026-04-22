package com.tripplanner.enums;

public enum CommuteMode {
    WALK,
    TAXI,
    SUBWAY,
    CAR,
    BUS,
    NONE;

    public static CommuteMode fromString(String value) {
        if (value == null) return null;
        try {
            return CommuteMode.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
