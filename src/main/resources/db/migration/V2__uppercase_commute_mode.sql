-- commute_mode was stored lowercase ("walk", "taxi"...).
-- PlaceType/CommuteMode are now Java enums mapped with @Enumerated(EnumType.STRING),
-- which serialises as the enum name (uppercase). Uppercase existing rows to match.
UPDATE itinerary_items
SET commute_mode = UPPER(commute_mode)
WHERE commute_mode IS NOT NULL;
