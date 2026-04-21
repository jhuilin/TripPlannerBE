# TripPlanner — Product & Technical Plan

## Product Vision

An AI-powered trip planner where users describe what they want and GPT-4o generates a complete itinerary in real time. Users can then refine restaurant and hotel picks per stop, manually or with AI assistance.

---

## User Flow (Wizard)

1. **Destination** — where to go
2. **Dates** — start date + number of days
3. **Trip Style** — pick up to 3 categories: Relax, Adventure, Local Culture, Nature, Photography, Kid-Friendly, Shopping
4. **Day Intensity** — Light / Balanced / Packed
5. **Budget** — total budget + currency
6. **Additional Info** *(optional)* — e.g. "I want to visit Fuji Mountain and stay 3 days"
7. **Generate** — SSE streaming; UI shows stops as they arrive on a live map
8. **Restaurants** — per day per stop: browse AI suggestions, search/filter, pick manually, or let AI decide (with optional preferences)
9. **Hotels** — per stop: browse AI suggestions, pick manually, or let AI decide (with optional preferences)
10. **Complete Trip** — full itinerary with location, restaurant, hotel, commute times, and daily budget

---

## Architecture

### Backend (this repo)
- Spring Boot 3.5 / Java 21
- Gradle Kotlin DSL
- PostgreSQL (JPA/Hibernate)
- OpenAI GPT-4o via `com.openai:openai-java:2.1.0`
- Google OAuth2 → JWT (access 15min, refresh 30d)
- SSE streaming with virtual threads

### Frontend (separate repo — TripPlannerFE)
- iOS app (planned)
- Also supports web (`http://localhost:3000` for local dev)

---

## Backend Implementation Status

### Completed
- [x] Google OAuth2 → JWT auth (access + refresh token rotation)
- [x] Trip wizard: `TripCreateRequest` (destination, dates, categories, intensity, budget, additionalInfo)
- [x] SSE streaming trip generation via GPT-4o (`POST /api/trips/generate`)
- [x] Trip CRUD (list, get, delete, update stop, delete stop)
- [x] `TripStopCost` with intercity transport type, public transport time, car time
- [x] Restaurant recommendations + manual select + AI-decide (with preferences)
- [x] Hotel recommendations + manual select + AI-decide (with preferences)
- [x] Packing list (CRUD + toggle)
- [x] Notifications (scheduled daily 9 AM, 3 days before trip start)
- [x] Swagger UI (`/swagger-ui/index.html`)
- [x] DB indexes on `restaurants` and `hotels` tables

### Next — Frontend (iOS)
- [ ] Google Sign-In SDK integration
- [ ] 6-step wizard UI
- [ ] SSE stream consumer with live map pins
- [ ] Trip list with map overview
- [ ] Restaurant / hotel selection flow
- [ ] Complete trip preview with daily budget breakdown

---

## Key Technical Decisions

| Decision | Rationale |
|----------|-----------|
| Google-only auth | Simplicity — no password management |
| Our own JWT over Google token | Stateless, short-lived, no Google dependency on each API call |
| SSE streaming | User sees the trip being generated stop-by-stop, no long wait |
| Virtual threads for SSE | Non-blocking, scales well for long-running OpenAI calls |
| `---STOP---` delimiter protocol | Lets us parse and emit each stop as it arrives in the stream |
| TripStatus computed dynamically | Never stale — always accurate to today's date |
| `findByIdAndUserId` everywhere | Ownership enforcement without a separate auth check layer |
| Preferences only on AI-decide, not browse | Browse is neutral discovery; preferences only matter when delegating to AI |

---

## DB Schema (auto-managed by Hibernate)

| Table | Key Columns |
|-------|-------------|
| `users` | id, email, name, google_sub |
| `refresh_tokens` | id, token, user_id, expiry |
| `trips` | id, user_id, destination, start_date, end_date, budget, currency, intensity |
| `trip_categories` | trip_id, category (join table for ElementCollection) |
| `trip_stops` | id, trip_id, location_name, lat, lng, arrival_date, departure_date, order_index, sub_places |
| `trip_stop_costs` | id, stop_id, intercity_transport, intercity_transport_type, local_transport, accommodation, food, activities, total, intercity_public_transport_time_mins, intercity_car_time_mins |
| `restaurants` | id, stop_id, day_date, name, cuisine, price_level, rating, address, lat, lng, ai_selected |
| `hotels` | id, stop_id, name, star_rating, price_per_night, address, lat, lng, ai_selected |
| `packing_items` | id, trip_id, category, item_name, quantity, checked |
| `notifications` | id, user_id, trip_id, message, sent, created_at |
