# TripPlanner Backend — Claude Context

## Project Overview
Spring Boot 3.5 / Java 21 REST API. AI-powered trip planning using Anthropic Claude. Google-only OAuth2 auth (no email/password). PostgreSQL via JPA.

## Build & Run

```bash
# Java 21 is at this path (installed via Homebrew, not the system default)
JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.10/libexec/openjdk.jdk/Contents/Home ./mvnw compile
JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.10/libexec/openjdk.jdk/Contents/Home ./mvnw spring-boot:run
```

Always prefix Maven commands with the JAVA_HOME above — the system default is Java 8.

## Auth Architecture

**Google OAuth2 → our own JWT** (do not remove JWT layer):
1. Frontend verifies with Google, gets a Google ID token
2. `POST /api/auth/google` — backend verifies ID token via `GoogleIdTokenVerifier`, finds/creates user, issues access + refresh token
3. All subsequent API calls use our JWT as `Authorization: Bearer <token>`

**Token lifetimes:**
- Access token: 15 minutes (JWT, verified locally — no DB hit)
- Refresh token: 30 days (UUID stored in `refresh_tokens` table, rotated on each use)

**Endpoints:**
- `POST /api/auth/google` — public, returns `{ token, refreshToken, email, name }`
- `POST /api/auth/refresh` — public, rotates refresh token
- `POST /api/auth/logout` — authenticated, deletes refresh token from DB

## Key Design Decisions

- `TripStatus` is **computed dynamically** in `TripService.computeStatus()` from stop dates — it is NOT reliably stored in the DB. Never filter DB queries by the `status` column.
- `parseAndSaveTrip()` lives in `TripService` (not `ChatController`) so it can be `@Transactional`.
- `ChatController` uses an in-memory `ConcurrentHashMap` for conversation history keyed by `userId:tripId`. Sessions are cleared after `/generate`.
- `ClaudeService` uses `Model.of("claude-sonnet-4-6")` — model specified as string because the SDK enum constants change between versions.
- `GoogleIdTokenVerifier` is initialized once in `@PostConstruct`, not per-request.
- `ObjectMapper` is injected as a Spring bean (not `new ObjectMapper()` inline).

## Notifications

- Scheduled daily at **9 AM** via `@Scheduled(cron = "0 0 9 * * *")`
- Notifies users **3 days before** trip start date
- Deduplication: skips if a pending (unsent) notification already exists for the trip

## Ownership / Security Pattern

All trip operations use `findByIdAndUserId(tripId, user.getId())` — never `findById()` alone. This prevents users from accessing or modifying other users' data. `parseAndSaveTrip()` also enforces this for re-generation.

## CORS

Configured in `SecurityConfig`. Allowed origins are set via `CORS_ALLOWED_ORIGINS` env var (default: `http://localhost:3000`). Can be comma-separated for multiple origins.

## Local Config

Secrets go in `src/main/resources/application-local.yml` (gitignored). The `local` Spring profile is active by default. Do not add secrets to `application.yml`.

Required secrets: `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` (Base64, min 32 bytes), `ANTHROPIC_API_KEY`, `GOOGLE_CLIENT_ID`.

## Package Structure

```
com.tripplanner
├── config/       SecurityConfig, AppConfig
├── controller/   Auth, Trip, Packing, Notification, Chat
├── service/      Auth, Trip, Packing, Notification, Claude
├── entity/       User, Trip, TripStop, TripStopCost, PackingItem, Notification, RefreshToken
├── repository/   Spring Data JPA interfaces
├── dto/          request/ and response/ records
├── security/     JwtUtil, JwtAuthFilter, UserDetailsServiceImpl
├── enums/        TripStatus, TripStyle, PackingCategory
└── exception/    GlobalExceptionHandler
```

## Anthropic SDK Notes (v0.8.0)

- Use `Model.of("claude-sonnet-4-6")` — not enum constants (they changed between versions)
- Content blocks: `block.isText()` and `block.asText().text()` — not instanceof casting
