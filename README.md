# TripPlanner Backend

Spring Boot REST API for TripPlanner — an AI-powered trip planning app. Users go through a wizard to describe their trip, then GPT-4o generates a full itinerary streamed in real time. After generation, users select restaurants and hotels for each stop.

## Tech Stack

- **Java 21** + **Spring Boot 3.5**
- **Gradle (Kotlin DSL)** — build system
- **PostgreSQL** — primary database (JPA/Hibernate, `ddl-auto: update`)
- **Spring Security** — stateless JWT auth
- **Google OAuth2** — only authentication method (no email/password)
- **JWT (jjwt 0.12.6)** — 15-min access token + 30-day refresh token with rotation
- **OpenAI Java SDK 2.1.0** — GPT-4o for trip generation, restaurant & hotel recommendations
- **Lombok** — boilerplate reduction
- **SpringDoc OpenAPI** — Swagger UI at `/swagger-ui/index.html`

## Project Structure

```
src/main/java/com/tripplanner/
├── config/
│   ├── AppConfig.java              # Scheduling, ObjectMapper bean
│   └── SecurityConfig.java         # JWT filter, CORS
├── controller/
│   ├── AuthController.java         # /api/auth/*
│   ├── TripController.java         # /api/trips/*
│   ├── RestaurantController.java   # /api/trips/{id}/stops/{id}/restaurants/*
│   ├── HotelController.java        # /api/trips/{id}/stops/{id}/hotels/*
│   ├── PackingController.java      # /api/trips/{id}/packing/*
│   └── NotificationController.java # /api/notifications/*
├── service/
│   ├── AuthService.java            # Google token verification, JWT issuance
│   ├── TripService.java            # Trip CRUD, SSE trip parsing + save
│   ├── OpenAIService.java          # GPT-4o streaming, restaurant/hotel recommendations
│   ├── RestaurantService.java
│   ├── HotelService.java
│   ├── PackingService.java
│   └── NotificationService.java    # Scheduled daily at 9 AM
├── entity/
│   ├── User.java
│   ├── Trip.java                   # categories (ElementCollection), intensity
│   ├── TripStop.java
│   ├── TripStopCost.java           # intercity transport times
│   ├── Restaurant.java
│   ├── Hotel.java
│   ├── PackingItem.java
│   ├── Notification.java
│   └── RefreshToken.java
├── repository/         # Spring Data JPA repositories
├── dto/
│   ├── request/        # TripCreateRequest, RestaurantSelectRequest, HotelSelectRequest, AiPreferencesRequest, ...
│   └── response/       # TripResponse, RestaurantResponse, HotelResponse, ...
├── security/
│   ├── JwtUtil.java
│   ├── JwtAuthFilter.java
│   └── UserDetailsServiceImpl.java
├── enums/
│   ├── TripStatus.java       # PLANNED, STARTED, ONGOING, COMPLETED (computed)
│   ├── TripCategory.java     # RELAX, ADVENTURE, LOCAL_CULTURE, NATURE, PHOTOGRAPHY, KID_FRIENDLY, SHOPPING
│   ├── DayIntensity.java     # LIGHT, BALANCED, PACKED
│   └── PackingCategory.java  # CLOTHING, TOILETRIES, ESSENTIALS, DESTINATION_SPECIFIC
└── exception/
    └── GlobalExceptionHandler.java
```

## API Endpoints

### Auth — `/api/auth`
| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/google` | Public | Verify Google ID token, return JWT pair |
| POST | `/refresh` | Public | Rotate refresh token, return new JWT pair |
| POST | `/logout` | Required | Delete refresh token |

### Trips — `/api/trips`
| Method | Path | Description |
|--------|------|-------------|
| GET | `/` | List current user's trips |
| GET | `/{tripId}` | Get trip with all stops and costs |
| DELETE | `/{tripId}` | Delete trip |
| POST | `/generate` | SSE stream — generate trip with GPT-4o |
| PATCH | `/{tripId}/stops/{stopId}` | Update a stop |
| DELETE | `/{tripId}/stops/{stopId}` | Delete a stop |

### Restaurants — `/api/trips/{tripId}/stops/{stopId}/restaurants`
| Method | Path | Description |
|--------|------|-------------|
| GET | `/recommendations?date=YYYY-MM-DD` | Get AI restaurant suggestions (browse) |
| POST | `/select` | Save a manually chosen restaurant |
| POST | `/ai-decide` | Let AI pick and save the top restaurant |

### Hotels — `/api/trips/{tripId}/stops/{stopId}/hotels`
| Method | Path | Description |
|--------|------|-------------|
| GET | `/recommendations` | Get AI hotel suggestions (browse) |
| POST | `/select` | Save a manually chosen hotel |
| POST | `/ai-decide` | Let AI pick and save the top hotel |

### Packing — `/api/trips/{tripId}/packing`
| Method | Path | Description |
|--------|------|-------------|
| GET | `/` | List packing items |
| POST | `/` | Add item |
| PATCH | `/{itemId}/toggle` | Toggle checked state |
| DELETE | `/{itemId}` | Delete item |

### Notifications — `/api/notifications`
| Method | Path | Description |
|--------|------|-------------|
| GET | `/` | Get pending notifications |
| POST | `/mark-sent` | Mark all notifications as sent |

## Trip Generation Flow

1. User completes wizard: destination → dates → categories (up to 3) → intensity → budget → (optional) additional info
2. `POST /api/trips/generate` — SSE streaming endpoint (`text/event-stream`)
3. Backend calls GPT-4o with streaming; each stop is emitted as an SSE `stop` event as generated
4. Final SSE `complete` event carries the saved trip ID
5. User then selects restaurants and hotels per stop (manual or AI-decided)

## Auth Flow

```
Frontend                    Backend                     Google
   |                           |                           |
   |── Google Sign-In ────────────────────────────────────>|
   |<── Google ID Token ────────────────────────────────── |
   |                           |                           |
   |── POST /api/auth/google ─>|                           |
   |   { idToken }             |── verify token ─────────>|
   |                           |<── payload (email, name) ─|
   |                           |── find/create user in DB  |
   |<── { token, refreshToken }|                           |
   |                           |                           |
   |── API calls with Bearer ─>|── validate JWT locally    |
```

Token refresh: when access token expires (15 min), call `POST /api/auth/refresh`. Old refresh token is deleted and a new one issued (rotation).

## Local Setup

### Prerequisites
- Java 21 (Homebrew: `brew install openjdk@21`)
- PostgreSQL running locally

### 1. Create the database
```bash
psql postgres -c "CREATE DATABASE tripplanner;"
```

### 2. Configure secrets
Fill in `src/main/resources/application-local.yml` (gitignored):
```yaml
spring:
  datasource:
    username: your_postgres_username
    password: your_postgres_password   # leave blank if none

jwt:
  secret: <base64 string — run: openssl rand -base64 32>

openai:
  api-key: sk-...    # platform.openai.com

google:
  client-id: ...apps.googleusercontent.com   # Google Cloud Console

cors:
  allowed-origins: http://localhost:3000
```

### 3. Run
```bash
JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.10/libexec/openjdk.jdk/Contents/Home ./gradlew bootRun
```

Server starts on `http://localhost:8080`.  
Swagger UI: `http://localhost:8080/swagger-ui/index.html`

## Environment Variables (production)

| Variable | Description |
|----------|-------------|
| `DB_USERNAME` | PostgreSQL username |
| `DB_PASSWORD` | PostgreSQL password |
| `JWT_SECRET` | Base64-encoded HMAC secret (min 32 bytes) |
| `OPENAI_API_KEY` | OpenAI API key |
| `GOOGLE_CLIENT_ID` | Google OAuth2 client ID |
| `CORS_ALLOWED_ORIGINS` | Allowed origins (default: `http://localhost:3000`) |

## Trip Status Logic

Status is computed dynamically from stop dates (never stored in DB):

| Status | Condition |
|--------|-----------|
| `PLANNED` | Today is before first stop arrival |
| `STARTED` | Today is within the first stop's date range |
| `ONGOING` | Today is past first stop but within last stop's departure |
| `COMPLETED` | Today is after last stop's departure |
