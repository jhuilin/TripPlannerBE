CREATE TABLE users (
    id         BIGSERIAL PRIMARY KEY,
    email      VARCHAR(255) NOT NULL UNIQUE,
    name       VARCHAR(255),
    google_id  VARCHAR(255) UNIQUE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE refresh_tokens (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users(id),
    token      VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP
);

CREATE TABLE wizard_drafts (
    user_id    BIGINT PRIMARY KEY REFERENCES users(id),
    draft_json TEXT NOT NULL,
    updated_at TIMESTAMP
);

CREATE TABLE trips (
    id                   BIGSERIAL PRIMARY KEY,
    user_id              BIGINT NOT NULL REFERENCES users(id),
    destination          VARCHAR(255) NOT NULL,
    title                VARCHAR(255),
    short_description    TEXT,
    additional_info      TEXT,
    confirmed            BOOLEAN NOT NULL DEFAULT FALSE,
    start_date           DATE NOT NULL,
    end_date             DATE NOT NULL,
    budget               NUMERIC(12,2) NOT NULL,
    currency             VARCHAR(3) NOT NULL DEFAULT 'USD',
    intensity            VARCHAR(50) NOT NULL,
    refine_count         INTEGER NOT NULL DEFAULT 0,
    packing_refine_count INTEGER NOT NULL DEFAULT 0,
    created_at           TIMESTAMP,
    updated_at           TIMESTAMP
);

CREATE TABLE trip_categories (
    trip_id  BIGINT NOT NULL REFERENCES trips(id),
    category VARCHAR(50) NOT NULL,
    PRIMARY KEY (trip_id, category)
);

CREATE TABLE trip_stops (
    id             BIGSERIAL PRIMARY KEY,
    trip_id        BIGINT NOT NULL REFERENCES trips(id),
    location_name  VARCHAR(255) NOT NULL,
    lat            DOUBLE PRECISION,
    lng            DOUBLE PRECISION,
    arrival_date   DATE NOT NULL,
    departure_date DATE NOT NULL,
    order_index    INTEGER NOT NULL
);

CREATE TABLE trip_stop_costs (
    id                                   BIGSERIAL PRIMARY KEY,
    stop_id                              BIGINT NOT NULL REFERENCES trip_stops(id),
    intercity_transport                  NUMERIC(10,2),
    intercity_transport_type             VARCHAR(50),
    local_transport                      NUMERIC(10,2),
    accommodation                        NUMERIC(10,2),
    food                                 NUMERIC(10,2),
    activities                           NUMERIC(10,2),
    total                                NUMERIC(10,2) NOT NULL,
    intercity_public_transport_time_mins INTEGER,
    intercity_car_time_mins              INTEGER
);

CREATE TABLE hotels (
    id              BIGSERIAL PRIMARY KEY,
    stop_id         BIGINT NOT NULL REFERENCES trip_stops(id),
    name            VARCHAR(255) NOT NULL,
    star_rating     INTEGER,
    price_per_night NUMERIC(10,2),
    address         VARCHAR(255),
    lat             DOUBLE PRECISION,
    lng             DOUBLE PRECISION,
    ai_selected     BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE restaurants (
    id          BIGSERIAL PRIMARY KEY,
    stop_id     BIGINT NOT NULL REFERENCES trip_stops(id),
    day_date    DATE,
    name        VARCHAR(255) NOT NULL,
    cuisine     VARCHAR(255),
    price_level INTEGER,
    rating      DOUBLE PRECISION,
    address     VARCHAR(255),
    lat         DOUBLE PRECISION,
    lng         DOUBLE PRECISION,
    ai_selected BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE itinerary_items (
    id                       BIGSERIAL PRIMARY KEY,
    stop_id                  BIGINT NOT NULL REFERENCES trip_stops(id),
    day_date                 DATE NOT NULL,
    order_index              INTEGER NOT NULL,
    place_name               VARCHAR(255) NOT NULL,
    place_type               VARCHAR(50),
    start_time               TIME,
    duration_mins            INTEGER,
    distance_from_prev_miles DOUBLE PRECISION,
    commute_from_prev_mins   INTEGER,
    commute_mode             VARCHAR(20),
    lat                      DOUBLE PRECISION,
    lng                      DOUBLE PRECISION
);

CREATE TABLE packing_items (
    id         BIGSERIAL PRIMARY KEY,
    trip_id    BIGINT NOT NULL REFERENCES trips(id),
    category   VARCHAR(50) NOT NULL,
    item_name  VARCHAR(255) NOT NULL,
    quantity   INTEGER NOT NULL DEFAULT 1,
    is_checked BOOLEAN NOT NULL DEFAULT FALSE
);
