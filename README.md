# SkillBook

A full-stack tutoring marketplace and real-time learning platform connecting students with independent tutors. Built as a production-style portfolio project demonstrating strong backend engineering: relational schema design, race-condition-safe booking, role-based access control, and third-party payment integration.

## Overview

Students discover tutors, view real-time availability, book sessions, and pay securely online. Tutors manage profiles, set availability, conduct video sessions, and build reputation through reviews.

## Features

- **Three user roles** — Student, Tutor, and Admin
- **Authentication** — JWT-based secure signup/login with password hashing
- **Tutor profiles** — subjects taught, hourly rate, bio, experience, ratings
- **Availability management** — weekly recurring and one-off slots
- **Concurrency-safe booking** — database-level constraints prevent double-booking
- **Smart tutor discovery** — skill-based search, price/rating filters, sorted paginated results
- **Skill match score** — explainable matching algorithm using normalized skill relationships
- **Stripe payments** — test-mode checkout with webhook verification
- **Cancellation & rescheduling** — configurable refund policies in the service layer
- **Real-time chat** — WebSocket messaging scoped to bookings
- **Video calling** — WebRTC with backend-managed meeting lifecycle
- **Reviews & ratings** — only after completed sessions, duplicate-proof
- **Email notifications** — booking confirmation and reminders via Spring Mail
- **Tutor analytics** — real DB queries (no hardcoded stats)

## Tech Stack

| Layer         | Technology                             |
| ------------- | -------------------------------------- |
| Backend       | Java 21, Spring Boot 3.3, Spring Web   |
| Persistence   | Spring Data JPA, Hibernate, Flyway     |
| Database      | PostgreSQL 15+ (16 recommended)        |
| Auth          | Spring Security, JWT (jjwt)            |
| Validation    | Jakarta Bean Validation                |
| Build         | Maven                                  |
| Frontend      | React 18 + TypeScript + Vite           |
| HTTP client   | Axios                                  |
| Routing       | React Router 6                         |
| Payments      | Stripe API (test mode)                 |
| Real-time     | WebSocket (chat) + WebRTC (video)      |
| Notifications | Spring Mail                            |
| Testing       | JUnit 5, Mockito, Spring Boot Test, H2 |
| Infra         | Docker (PostgreSQL)                    |

## Architecture

**Modular monolith** — a single Spring Boot application organized by domain. Each domain (`auth`, `user`, `tutor`, `skill`, `availability`, `booking`, `payment`, `meeting`, `review`, `notification`) is self-contained with its own controller/service/repository/entity/dto layers.

```
SkillBook/
├── backend/                          # Spring Boot application (port 8080)
│   ├── src/main/java/com/skillbook/
│   │   ├── SkillBookApplication.java
│   │   ├── common/                   # BaseEntity, ApiResponse
│   │   ├── exception/                # GlobalExceptionHandler, ResourceNotFoundException
│   │   ├── config/                   # SecurityConfig
│   │   ├── auth/                     # (Phase 3)
│   │   ├── user/                     # (Phase 3)
│   │   ├── tutor/                    # (Phase 4)
│   │   ├── skill/                    # (Phase 5)
│   │   ├── availability/             # (Phase 6)
│   │   ├── booking/                  # (Phase 7)
│   │   ├── payment/                  # (Phase 8)
│   │   ├── meeting/                  # (Phase 11)
│   │   ├── review/                   # (Phase 12)
│   │   └── notification/             # (Phase 9)
│   └── src/main/resources/
│       ├── application.yml           # shared config
│       ├── application-local.yml     # local PostgreSQL
│       ├── application-test.yml      # H2 for tests
│       └── db/migration/             # Flyway versioned SQL
├── frontend/                         # React + Vite app (port 5173)
│   ├── src/
│   │   ├── pages/                    # Login, Register, dashboards
│   │   ├── components/               # Reusable UI
│   │   ├── services/                 # Axios API layer
│   │   ├── types/                    # TypeScript interfaces
│   │   └── styles/
├── docker-compose.yml                # PostgreSQL for Docker setup
├── .env.example                      # Template for all environment variables
└── README.md
```

## Database Schema

Core entities (built across phases):

- **users** — id (UUID), email, password_hash, name, role
- **tutor_profiles** — bio, experience, hourly_rate, rating (FK → users)
- **skills** — normalized skill registry
- **availability** — weekly (day_of_week) and one-off (date) slots
- **bookings** — tutor, student, start/end time, status, price
- **payments** — booking, amount, stripe_payment_id, status
- **meetings** — booking, start/end, meeting URL
- **reviews** — student, tutor, booking, rating, comment
- **notifications** — user, type, message, read flag
- **messages** — booking, sender, content, timestamp

Schema is versioned by Flyway migrations in `backend/src/main/resources/db/migration/`. The initial migration (`V1__init_schema.sql`) creates the `users` table with a UUID primary key and a unique email constraint.

---

## Quick Start (2 minutes)

```bash
# 1. Start PostgreSQL — Docker is the recommended path
docker-compose up -d

# 2. Backend (JDK 21 required — see Troubleshooting)
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
cd backend && mvn spring-boot:run

# 3. Frontend (new terminal)
cd frontend && npm install && npm run dev
```

Open **http://localhost:5173** — the Login page loads, linked to Register.

---

## Detailed Setup

### Prerequisites

| Tool       | Version              | Notes                                         |
| ---------- | -------------------- | --------------------------------------------- |
| Java       | **JDK 21** (Temurin) | JDK 26 breaks Lombok annotation processing    |
| Maven      | 3.9+                 | Runs via `mvn` (not `./mvnw`) in this project |
| Node.js    | 18+                  | For the React frontend                        |
| PostgreSQL | 15+ / Docker         | Either a running instance or Docker Desktop   |

### 1. Database

The backend connects to `jdbc:postgresql://localhost:5433/skillbook` with credentials from env vars (defaults: `postgres` / `password`).

**Option A — Docker (recommended):**

```bash
docker-compose up -d
```

This starts `postgres:16-alpine` mapped to host port **5433** (so it coexists with any local PostgreSQL already on 5432), with database `skillbook`, user `postgres`, password `password`, and a healthcheck. The database is created automatically by the container.

```bash
# Verify
docker ps                        # skillbook-db should be 'healthy'
docker compose logs postgres      # terse 'database system is ready'

# Inspect the DB (run inside the container — it has its own psql)
docker exec skillbook-db psql -U postgres -d skillbook -c '\dt'
```

**Option B — Native Homebrew PostgreSQL:**

```bash
brew install postgresql@15       # if not installed (17.10 also works)
brew services start postgresql@15

createdb skillbook               # create the database (one-time)
psql -d skillbook -c '\dt'        # verify: flyway_schema_history + users
```

Point the app at the native instance on 5432 with env vars:

```bash
export DB_URL=jdbc:postgresql://localhost:5432/skillbook
```

**Option C — Use an existing PostgreSQL** locally (any version 15+):

```bash
createdb skillbook || true        # create only if it doesn't exist
export DB_URL=jdbc:postgresql://localhost:5432/skillbook   # adjust port if needed
```

> On first backend startup, Flyway runs `V1__init_schema.sql` automatically — no manual SQL needed.

### 2. Environment variables

Copy the template and fill in values. The backend reads these via the shell (Spring Boot resolves `var:default` in `application-local.yml`):

```bash
cp .env.example .env
```

Only `DB_*` vars are needed for Phase 1. Set them in your shell profile (`~/.zshrc`) or export per terminal:

```bash
export DB_USERNAME="postgres"                          # or your local PG user
export DB_PASSWORD="password"                          # empty string works with Homebrew trust auth
export JWT_SECRET="$(openssl rand -base64 48)"         # Phase 3
```

Full variable reference:

| Variable                           | Used From | Default                                      | Purpose                           |
| ---------------------------------- | --------- | -------------------------------------------- | --------------------------------- |
| `DB_URL`                           | Phase 1   | `jdbc:postgresql://localhost:5433/skillbook` | JDBC connection string            |
| `DB_USERNAME`                      | Phase 1   | `postgres`                                   | PostgreSQL user                   |
| `DB_PASSWORD`                      | Phase 1   | `password`                                   | PostgreSQL password               |
| `JWT_SECRET`                       | Phase 3   | —                                            | JWT signing secret (min 32 chars) |
| `JWT_EXPIRATION_MS`                | Phase 3   | `86400000`                                   | Token lifetime in ms              |
| `STRIPE_SECRET_KEY`                | Phase 8   | —                                            | Stripe test-mode API key          |
| `STRIPE_WEBHOOK_SECRET`            | Phase 8   | —                                            | Stripe webhook signing secret     |
| `MAIL_HOST/PORT/USERNAME/PASSWORD` | Phase 9   | —                                            | SMTP credentials                  |

### 3. Backend

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home   # REQUIRED
cd backend
mvn spring-boot:run
```

Flyway applies pending migrations, then Tomcat starts on **http://localhost:8080/api**.

> **JWT_SECRET is required.** The backend fails fast at startup if `JWT_SECRET` is missing or under 32 chars. Export it before running:
> ```bash
> export JWT_SECRET="$(openssl rand -base64 48)"
> ```

Verify:

```bash
curl http://localhost:8080/api/actuator/health
# {"status":"UP"}
```

### Auth API quick test

```bash
# register (public)
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name":"Alice","email":"alice@example.com","password":"password123","role":"TUTOR"}'

# login (public) -> returns token in data.token
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","password":"password123"}'

# me (requires Bearer token)
curl http://localhost:8080/api/auth/me -H "Authorization: Bearer <token>"
```

### 4. Frontend

```bash
cd frontend
npm install        # first time only
npm run dev
```

Vite serves on **http://localhost:5173** and proxies `/api/*` to `http://localhost:8080` (configured in `vite.config.ts`), so the browser never hits cross-origin issues and the API base URL is just `/api`.

---

## Running Tests

Backend tests use an in-memory H2 database (`application-test.yml`) — **no PostgreSQL required**:

```bash
cd backend
mvn test
```

Current suite: Spring context load, actuator health endpoint, and 5 `GlobalExceptionHandler` unit tests (validation→400, illegal argument→400, conflict→409, not-found→404, generic→500 with no internal details leaked).

---

## Docker

`docker-compose.yml` currently runs **PostgreSQL only** on host port **5433** (container's native 5432). The Spring Boot app and React app run locally during development (rebuilding Docker images on every code change would slow the loop). Containerizing the full stack lands in Phase 15.

```bash
docker-compose up -d       # start
docker-compose down        # stop (keeps the volume: pgdata)
docker-compose down -v     # stop AND delete data
```

> Note: because the container is mapped to 5433, you can keep a local PostgreSQL running on 5432 at the same time. The app's default config targets 5433 (Docker); export `DB_URL=jdbc:postgresql://localhost:5432/skillbook` to use a native instance instead.

---

## Stopping / Restarting

```bash
# Frontend + backend (dev-run processes)
# Ctrl+C works when running in terminals; for background processes:
lsof -iTCP:8080 -sTCP:LISTEN    # find backend PID
lsof -iTCP:5173 -sTCP:LISTEN    # find frontend PID
kill <pid>

# Restart backend after code changes while PostgreSQL stays up:
cd backend && mvn spring-boot:run
```

---

## Troubleshooting

**Maven compile fails with `cannot find symbol: builder()` / missing getters (Lombok)**

Maven is using JDK 26 (Homebrew `openjdk`), which Lombok in Spring Boot 3.3 does not support. Point Maven at JDK 21:

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
mvn -version   # confirm 'Java version: 21.0.x' before building
```

**Log shows `Using generated security password: ...`**

Harmless. Spring Security's default in-memory user is created because no `UserDetailsService` exists yet. `config/SecurityConfig.java` currently permits all endpoints; Phase 3 replaces this with JWT auth — the random password is never used.

**Log shows `Flyway upgrade recommended: PostgreSQL 17.10 is newer...`**

A warning only. Spring Boot 3.3.5 ships Flyway tested against PostgreSQL 16; newer servers work fine. Use PostgreSQL 16 via Docker if you want it silenced.

**`curl /api/actuator/health` returns 401**

The app is running under the default Spring Security config (no `SecurityConfig` applied). Confirm `SecurityConfig.java` exists under `config/`; `ddl-auto` and Flyway don't affect this. Rebuild with `mvn clean spring-boot:run`.

**Port already in use (8080 or 5173)**

```bash
lsof -iTCP:8080 -sTCP:LISTEN   # kill the listed PID
```

---

## Engineering Challenges

- **Double-booking prevention** — handled at the database level with transactions and row locking, not just application-side `if (slotAvailable)` checks (Phase 7)
- **Timezone-safe scheduling** — Java's `ZonedDateTime`/`ZoneId` APIs, consistent UTC storage (Phase 6)
- **Payment consistency** — booking state and payment state kept in sync via Stripe webhooks, never trusting frontend responses (Phase 8)
- **Explainable recommendations** — skill match score computed from normalized relational data; no black-box ML (Phase 5/13)
- **Schema ownership** — Flyway versioned migrations + Hibernate `ddl-auto: validate`, so schema drift fails loudly at startup

## Development Phases

| Phase | Scope                                                               | Status  |
| ----- | ------------------------------------------------------------------- | ------- |
| 1     | Project foundation: Spring Boot, PostgreSQL, Flyway, React scaffold | ✅ Done |
| 2     | Database entities + JPA relationships                               | ✅ Done |
| 3     | Authentication + JWT + roles                                        | ✅ Done |
| 4     | Student/Tutor profiles                                              |         |
| 5     | Skills + tutor discovery + search                                   |         |
| 6     | Availability + slot generation                                      |         |
| 7     | Concurrency-safe booking                                            |         |
| 8     | Stripe payments                                                     |         |
| 9     | Notifications + session lifecycle                                   |         |
| 10    | WebSocket chat                                                      |         |
| 11    | WebRTC video sessions                                               |         |
| 12    | Reviews + tutor reputation                                          |         |
| 13    | Tutor analytics + recommendation ranking                            |         |
| 14    | Testing + security review                                           |         |
| 15    | Docker + deployment + documentation                                 |         |

## License

For educational/portfolio purposes.
