package com.tripplanner.enums;

public enum PlaceType {
    ACCOMMODATION,
    RESTAURANT,
    ATTRACTION,
    TRANSPORT;

    public static PlaceType fromString(String value) {
        if (value == null) return null;
        try {
            return PlaceType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
