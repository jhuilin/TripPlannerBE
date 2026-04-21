# TripPlanner Backend — Claude Context

## Project Overview
Spring Boot 3.5 / Java 21 REST API. AI-powered trip planning using OpenAI GPT-4o. Google-only OAuth2 auth. PostgreSQL via JPA. **Build system: Gradle (Kotlin DSL).**

## Build & Run

```bash
# Java 21 is at this path (installed via Homebrew, not the system default)
JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.10/libexec/openjdk.jdk/Contents/Home ./gradlew compileJava
JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.10/libexec/openjdk.jdk/Contents/Home ./gradlew bootRun
```

Always prefix Gradle commands with the JAVA_HOME above — the system default is Java 8.

## Trip Creation Flow (Wizard)

1. User fills 6-step wizard: destination → dates → categories (up to 3) → intensity → budget → preview
2. POST /api/trips/generate — SSE streaming endpoint
3. Backend calls OpenAI GPT-4o; each stop is streamed as an SSE `stop` event as it's generated
4. Final SSE `complete` event carries the saved trip ID
5. After generation: user selects restaurants and hotels per stop

## Auth Architecture

**Google OAuth2 → our own JWT** (do not remove JWT layer):
1. Frontend verifies with Google, gets a Google ID token
2. `POST /api/auth/google` — backend verifies ID token via `GoogleIdTokenVerifier`, finds/creates user, issues access + refresh token
3. All subsequent API calls use our JWT as `Authorization: Bearer <token>`

**Token lifetimes:**
- Access token: 15 minutes (JWT, verified locally — no DB hit)
- Refresh token: 30 days (UUID stored in `refresh_tokens` table, rotated on each use)

**Endpoints:**
- `POST /api/auth/google` — public
- `POST /api/auth/refresh` — public, rotates refresh token
- `POST /api/auth/logout` — authenticated, deletes refresh token from DB

## Key Design Decisions

- `TripStatus` is **computed dynamically** in `TripService.computeStatus()` from stop dates — never filter DB queries by the `status` column.
- `parseAndSaveFromStops()` lives in `TripService` so it can be `@Transactional`.
- OpenAI streaming: `OpenAIService.generateTripStream()` accumulates tokens, detects `---STOP---` delimiter to emit each stop as SSE, then saves trip at end.
- `GoogleIdTokenVerifier` is initialized once in `@PostConstruct`, not per-request.
- `ObjectMapper` is injected as a Spring bean (not `new ObjectMapper()` inline).
- All trip operations use `findByIdAndUserId(tripId, userId)` — never `findById()` alone.

## Notifications

- Scheduled daily at **9 AM** via `@Scheduled(cron = "0 0 9 * * *")`
- Notifies users **3 days before** trip start date
- Deduplication: skips if a pending (unsent) notification already exists for the trip

## CORS

Configured in `SecurityConfig`. Allowed origins via `CORS_ALLOWED_ORIGINS` env var (default: `http://localhost:3000`).

## Local Config

Secrets go in `src/main/resources/application-local.yml` (gitignored). The `local` Spring profile is active by default.

Required secrets: `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` (Base64, min 32 bytes), `OPENAI_API_KEY`, `GOOGLE_CLIENT_ID`.

## Package Structure

```
com.tripplanner
├── config/       SecurityConfig, AppConfig
├── controller/   Auth, Trip, Packing, Notification, Restaurant, Hotel
├── service/      Auth, Trip, Packing, Notification, OpenAI, Restaurant, Hotel
├── entity/       User, Trip, TripStop, TripStopCost, PackingItem, Notification, RefreshToken, Restaurant, Hotel
├── repository/   Spring Data JPA interfaces
├── dto/          request/ and response/ records
├── security/     JwtUtil, JwtAuthFilter, UserDetailsServiceImpl
├── enums/        TripStatus, TripCategory, DayIntensity, PackingCategory
└── exception/    GlobalExceptionHandler
```

## OpenAI SDK Notes (v2.1.0)

- Import paths: `com.openai.models.chat.completions.ChatCompletionCreateParams`, `com.openai.models.ChatModel`
- `StreamResponse<ChatCompletionChunk>` is at `com.openai.core.http.StreamResponse`
- `client.chat().completions().create(params)` — non-streaming
- `client.chat().completions().createStreaming(params)` — returns `StreamResponse<ChatCompletionChunk>`
- `chunk.choices().get(0).delta().content().orElse("")` — extract token delta
- `maxTokens(Long)` — takes a Long value
