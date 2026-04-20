# TripPlanner Backend

Spring Boot REST API for TripPlanner — an AI-powered trip planning app that uses Claude to generate itineraries, manage trip stops, packing lists, and user notifications.

## Tech Stack

- **Java 21** + **Spring Boot 3.5**
- **PostgreSQL** — primary database (JPA/Hibernate, `ddl-auto: update`)
- **Spring Security** — stateless JWT auth
- **Google OAuth2** — only authentication method (no email/password)
- **JWT (jjwt 0.12.6)** — 15-min access token + 30-day refresh token with rotation
- **Anthropic Claude SDK 0.8.0** — AI trip planning via `claude-sonnet-4-6`
- **Lombok** — boilerplate reduction

## Project Structure

```
src/main/java/com/tripplanner/
├── config/
│   ├── AppConfig.java          # Scheduling enabled
│   └── SecurityConfig.java     # JWT filter, CORS
├── controller/
│   ├── AuthController.java     # /api/auth/*
│   ├── TripController.java     # /api/trips/*
│   ├── PackingController.java  # /api/trips/{id}/packing/*
│   ├── NotificationController.java
│   └── ChatController.java     # /api/chat/*
├── service/
│   ├── AuthService.java        # Google token verification, JWT issuance
│   ├── TripService.java        # Trip CRUD + AI response parsing
│   ├── PackingService.java
│   ├── NotificationService.java # Scheduled daily at 9 AM
│   └── ClaudeService.java      # Anthropic API wrapper
├── entity/
│   ├── User.java
│   ├── Trip.java
│   ├── TripStop.java
│   ├── TripStopCost.java
│   ├── PackingItem.java
│   ├── Notification.java
│   └── RefreshToken.java
├── repository/         # Spring Data JPA repositories
├── dto/
│   ├── request/        # GoogleAuthRequest, RefreshTokenRequest, etc.
│   └── response/       # AuthResponse, TripResponse, etc.
├── security/
│   ├── JwtUtil.java
│   ├── JwtAuthFilter.java
│   └── UserDetailsServiceImpl.java
├── enums/
│   ├── TripStatus.java    # PLANNED, STARTED, ONGOING, COMPLETED
│   ├── TripStyle.java     # TIGHT, LOOSE, MIXED
│   └── PackingCategory.java
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

**Login response:**
```json
{
  "token": "<15-min access JWT>",
  "refreshToken": "<30-day UUID>",
  "email": "user@gmail.com",
  "name": "John Doe"
}
```

### Trips — `/api/trips`
| Method | Path | Description |
|--------|------|-------------|
| GET | `/` | List current user's trips |
| GET | `/{tripId}` | Get trip with stops and costs |
| DELETE | `/{tripId}` | Delete trip |
| PATCH | `/{tripId}/stops/{stopId}` | Update a stop |
| DELETE | `/{tripId}/stops/{stopId}` | Delete a stop |

### Packing — `/api/trips/{tripId}/packing`
| Method | Path | Description |
|--------|------|-------------|
| GET | `/` | List packing items |
| POST | `/` | Add item |
| PATCH | `/{itemId}/toggle` | Toggle checked state |
| DELETE | `/{itemId}` | Delete item |

### Chat — `/api/chat`
| Method | Path | Description |
|--------|------|-------------|
| POST | `/message` | Send message to Claude AI, get reply |
| POST | `/generate` | Finalize conversation → save trip to DB |

### Notifications — `/api/notifications`
| Method | Path | Description |
|--------|------|-------------|
| GET | `/` | Get pending notifications |
| POST | `/mark-sent` | Mark all notifications as sent |

Notifications are scheduled automatically at **9 AM daily** for trips starting **3 days** from now.

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
   |── API calls with Bearer ─>|                           |
   |   Authorization header    |── validate JWT locally    |
```

**Token refresh:** When access token expires (15 min), call `POST /api/auth/refresh` with the refresh token. Old refresh token is deleted and a new one is issued (rotation).

## Local Setup

### Prerequisites
- Java 21 (`/opt/homebrew/Cellar/openjdk@21/...` on macOS via Homebrew)
- PostgreSQL running locally

### 1. Create the database
```sql
CREATE DATABASE tripplanner;
```

### 2. Configure secrets
Fill in `src/main/resources/application-local.yml` (gitignored):
```yaml
spring:
  datasource:
    username: postgres
    password: your_postgres_password

jwt:
  secret: <base64 string from `openssl rand -base64 32`>

anthropic:
  api-key: sk-ant-...       # console.anthropic.com

google:
  client-id: ...apps.googleusercontent.com   # Google Cloud Console

cors:
  allowed-origins: http://localhost:3000
```

### 3. Run
```bash
JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.10/libexec/openjdk.jdk/Contents/Home \
  ./mvnw spring-boot:run
```

Server starts on `http://localhost:8080`.

## Environment Variables (production)

| Variable | Description |
|----------|-------------|
| `DB_USERNAME` | PostgreSQL username |
| `DB_PASSWORD` | PostgreSQL password |
| `JWT_SECRET` | Base64-encoded HMAC secret (min 32 bytes) |
| `ANTHROPIC_API_KEY` | Anthropic API key |
| `GOOGLE_CLIENT_ID` | Google OAuth2 client ID |
| `CORS_ALLOWED_ORIGINS` | Comma-separated allowed origins (default: `http://localhost:3000`) |

## Trip Status Logic

Status is computed dynamically from stop dates (not stored):

| Status | Condition |
|--------|-----------|
| `PLANNED` | Today is before first stop arrival |
| `STARTED` | Today is within the first stop's date range |
| `ONGOING` | Today is past first stop but within last stop's departure |
| `COMPLETED` | Today is after last stop's departure |
