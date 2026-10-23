# SkillBook — How Everything Works (Deep Dive)

A complete walkthrough of the SkillBook platform: architecture, every moving
part, the request lifecycle, the database, and each domain — written from the
actual source code, not from memory.

> Companion files: `api-endpoints.txt` (every HTTP endpoint), `README.md`
> (setup/run), `.claude/plan/*.md` (per-phase design notes),
> `.claude/tdd/*.tdd.md` (verification evidence).

---

## 1. High-Level Architecture

```
┌──────────────────────────┐        /api      ┌──────────────────────────────────┐
│  Frontend: React SPA      │ ───────────────▶│  Backend: Spring Boot (Java 21)   │
│  Vite dev server          │   HTTPS/JSON    │  single modular monolith          │
│  http://localhost:5173     │                │  http://localhost:8080/api         │
│  React Router, Axios,     │◀───────────────│                                    │
│  Tailwind CSS             │   ApiResponse   │                                    │
└──────────────────────────┘        envelope  └──────────┬─────────────────────────┘
                                                         │ JDBC (HikariCP)
                                                         ▼
                                             ┌──────────────────────┐
                                             │ PostgreSQL 16         │
                                             │ docker, :5433         │
                                             │ schema "public", v1..v6│
                                             └──────────────────────┘
```

- **Modular monolith backend.** One Spring Boot app, organized by domain
  (`auth`, `user`, `tutor`, `skill`, `availability`, `booking`, `payment`,
  `notification`). Each domain owns its controller → service → repository →
  entity → dto layers.
- **Field entities:** `JpaAuthenticationFilter` (JWT), `BCryptPasswordEncoder`,
  `GlobalExceptionHandler` (`@RestControllerAdvice`), Stripe SDK via a
  `StripeGateway` interface (mockable), `JavaMailSender` (disabled by default),
  Spring `@Scheduled` jobs.
- **Frontend:** React 18 SPA with TypeScript, React Router, Axios (one shared
  `api` client), Tailwind CSS (custom design tokens: `text-*`, `bg-*`,
  `border-*`, `primary`, `surface`…). State is minimal — component-local +
  `localStorage` for the token/user.

---

## 2. Tech Stack

| Layer | Technology | Version / Notes |
|-------|-----------|-----------------|
| Language | Java (backend) | **21** (compiled for 17+; run with Temurin 21) |
| Framework | Spring Boot | 3.3.5 (Spring 6.1, `jakarta.*` namespace) |
| Persistence | Spring Data JPA / Hibernate | 6.5.3, DDL `validate` in prod, `create-drop` in tests |
| Migrations | Flyway | `backend/src/main/resources/db/migration/V*.sql` |
| Database | PostgreSQL | 16-alpine in Docker, host port **5433** |
| Security | Spring Security + JJWT | stateless JWT (HS256), method security |
| Payments | Stripe SDK | hosted Checkout + webhooks + refunds, behind `StripeGateway` |
| Email | Spring Boot Mail | `JavaMailSender`, disabled unless `MAIL_ENABLED=true` |
| Build | Maven | `com.skillbook:skillbook` |
| Frontend | React 18 + TypeScript | Vite 5.4, React Router, Axios, Tailwind |
| Tests | JUnit 5 / Spring Boot Test | H2 in-memory (`application-test.yml`), MockMvc | 
| Environment | Docker Compose | only PostgreSQL (app runs locally) |

---

## 3. Repository Layout

```
SkillBook/
├── api-endpoints.txt                     # every HTTP endpoint + auth
├── docker-compose.yml                    # PostgreSQL 16 container
├── .env.example                          # template env vars (never commit secrets)
├── README.md                             # setup, env reference, phase table
├── rules.md                              # operator rules (git/DB permissions)
├── .claude/
│   ├── plan/                             # per-phase design docs (phase1..phase9)
│   └── tdd/                              # per-phase test-evidence reports
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/skillbook/
│       │   ├── SkillBookApplication.java # @SpringBootApplication @EnableScheduling
│       │   ├── common/                   # ApiResponse, BaseEntity, Role, exceptions
│       │   ├── config/                   # SecurityConfig (+ CORS) 
│       │   ├── exception/                # GlobalExceptionHandler
│       │   ├── auth/                     # register/login, JWT service + filter, JwtProperties
│       │   ├── user/                     # StudentProfile
│       │   ├── tutor/                    # TutorProfile, search, skills offered
│       │   ├── skill/                    # skill registry
│       │   ├── availability/             # AvailabilityRule / AvailabilitySlot
│       │   ├── booking/                  # Booking, BookingService, SessionScheduler
│       │   ├── payment/                  # Payment, PaymentService, StripeGateway(Impl)
│       │   └── notification/             # Notification, MailService, SessionScheduler hook
│       ├── main/resources/
│       │   ├── application.yml           # shared config (mail, jwt, scheduler, health)
│       │   ├── application-local.yml     # datasource → localhost:5433
│       │   ├── application-test.yml      # H2, mail+scheduler disabled
│       │   └── db/migration/             # V1__init..V6__notification_tables.sql
│       └── test/java/…                   # per-package integration + unit tests
└── frontend/
    └── src/
        ├── App.tsx                       # route table + AppShell wrapper
        ├── main.tsx                      # React root, theme toggle bootstrap
        ├── types/index.ts                # shared API types
        ├── services/                     # api.ts client + auth/availability/bookings/…
        └── components/ + pages/          # AppShell, NotificationBell, Discover, …
```

---

## 4. Database Schema (v6)

All tables use **UUID** PK (`BaseEntity` → `@GeneratedValue(UUID)`), plus
`created_at` / `updated_at`.

| Table | Key columns | Relationships / constraints |
|-------|-------------|----------------------------|
| `users` | email (unique), password_hash, name, role | role ∈ `STUDENT / TUTOR / ADMIN` |
| `tutor_profiles` | bio, hourly_rate (8,2), experience_years, rating | FK `user_id` → users (1:1) |
| `student_profiles` | bio, learning_goals | FK `user_id` → users (1:1) |
| `skills` / `skill_offerings` / `student_skills` | registry + tutor/student links | many-to-many resolved with join tables |
| `availability_rules` | day_of_week, start_time, end_time, timezone, active | UK (tutor, day, start) |
| `availability_slots` | start_at, end_at (UTC offsets), status, source | UK (tutor, start); status ∈ `AVAILABLE/BOOKED`; source ∈ `RULE/ONE_OFF`; FK rule_id |
| `bookings` | amount, currency, status, message | UK `slot_id` (**one booking per slot**); status ∈ `CONFIRMED/CANCELLED/COMPLETED`; cancellation columns (fee, refund, by, at); `reminder_sent_at` (Phase 9) |
| `payments` | amount, status, stripe session/intent/refund ids | UK `booking_id` (one payment per booking), UK `stripe_checkout_session_id`; status ∈ `PENDING/PAID/FAILED/REFUNDED/PARTIALLY_REFUNDED` |
| `notifications` | type, message, is_read | FK `user_id` (CASCADE), FK `booking_id` (SET NULL); type ∈ 7 notification kinds |
| `meetings`, `reviews`, `messages` | (Phase 10–12 future) | schema stubs only |

**One booking per slot** is enforced at the DB level (`uk_bookings_slot`). Slot
`BOOKED` state is also set in the transaction — the DB constraint is the
backstop that makes booking concurrency-safe.

---

## 5. Backend Deep Dive

### 5.1 The request lifecycle (every request)

```
HTTP request
   │
   ▼
Tomcat (context-path /api)
   │
   ▼
CORS filter (open: all origins, all methods/headers, credentials)   [SecurityConfig]
   │
   ▼
JwtAuthenticationFilter  ── has `Authorization: Bearer <token>`?
   ├─ yes: parse+verify JWT (HS256), load user, set SecurityContext
   └─ no:  continue unauthenticated
   │
   ▼
authorizeHttpRequests  (permitAll vs authenticated vs hasRole('X'))
   │
   ▼
Controller (validation annotations, @PreAuthorize)
   │
   ▼
@Service (@Transactional) → Repository → Hibernate → PostgreSQL
   │
   ▼
Json serialization: ApiResponse{ success, data|error, timestamp }
   │
   ▼
HTTP JSON response
```

### 5.2 The response envelope — `ApiResponse<T>`

Every endpoint returns `ApiResponse`: `{ "success": true, "data": … }` or
`{ "success": false, "error": "…" }`, always with a `timestamp`.
Controllers never construct error bodies — exceptions propagate to
`GlobalExceptionHandler`, which maps each exception type to a status + envelope:

| Exception | HTTP | Who throws it |
|-----------|------|---------------|
| `MethodArgumentNotValidException` (bean validation) | 400 + field map | Spring on `@Valid` DTOs |
| `IllegalArgumentException` | 400 | e.g. "Only students can create bookings" |
| `IllegalStateException` | 409 Conflict | e.g. "Slot is no longer available" |
| `BadCredentialsException` | 401 | login failure |
| `AccessDeniedException` | 403 | role mismatch / `@PreAuthorize` |
| `ResourceNotFoundException` | 404 | "X not found with Y: value" (also covers *cross-user* privacy — N+1-safe) |
| `PaymentSignatureException` | 401 | Stripe webhook with bad signature |
| `PaymentGatewayException` | 502 | Stripe call failed |
| `DataIntegrityViolationException` | 409 | unique-constraint race |
| anything else | 500 | generic, no internal detail leaked |

**Privacy convention:** "read someone else's thing" returns **404**, not 403,
so an attacker can't probe what IDs exist.

### 5.3 Auth (`auth` domain)

- **Register** `POST /auth/register` (public): lowercase/trim email, check
  uniqueness → `BCryptPasswordEncoder.encode(password)` → save → immediately
  return a token. **Role is taken from the request body** — this includes
  `ADMIN` (see Known Gaps §11).
- **Login** `POST /auth/login` (public): lookup by email → `matches()` →
  return token + user.
- **Token** `JwtService`: HS256 HMAC key built from `jwt.secret`
  (`JWT_SECRET`, enforced ≥ 32 chars at startup via `JwtProperties`). Claims:
  `sub` = email, `role`, `iat`, `exp` (default 24h, `JWT_EXPIRATION_MS`).
  Stateless — no server-side session.
- **Every subsequent request:** `JwtAuthenticationFilter` parses the token,
  sets the `Authentication` with principal = user email, authorities from the
  `role` claim. Controllers take `Authentication` and use `getName()` as the
  current user identity (never trust user-supplied IDs).
- **Roles:** `STUDENT`, `TUTOR`, `ADMIN`. Enforcement via
  `@PreAuthorize("hasRole('X')")` on controller methods **and**
  per-route matchers in `SecurityConfig`.

### 5.4 Profiles & discovery (`user`, `tutor`, `skill`)

- **Tutor setup:** `PUT /tutors/profile` (TUTOR) upserts bio/rate/experience;
  `PUT /tutors/profile/skills` links tutor → skills. Public view
  `GET /tutors/{userId}` for anyone; `GET /tutors/profile` (authenticated)
  returns *your own* when a tutor views it.
- **Search** `GET /tutors` (public): filters by `q` (name/bio substring),
  `skill`, `minRate/maxRate`, `minRating`; sort keys `rating_desc` (default),
  `rating_asc`, `price_asc`, `price_desc`, `name_asc/desc`. Paginated
  (`page`/`size`, size clamped 1–100). Returns tutor profiles with an
  **all-once skills batch load** (no N+1). 
- **Skills registry** `GET /skills` (public), `POST /skills` (authenticated —
  anyone may add a skill).

### 5.5 Availability (`availability`)

- **Rules** (recurring): tutor sets day-of-week windows (Mon 09:00–11:00 etc.)
  with a timezone. UK (tutor, day, start) prevents duplicate rules.
- **Slots** (concrete): `start_at`/`end_at` stored **normalized to UTC**
  (`withOffsetSameInstant(UTC)`); either generated from a rule (`RULE`) or
  manually added (`ONE_OFF`).
- **Endpoints:** `PUT/DELETE /availability/rules`, `POST/DELETE
  /availability/slots`, `POST /availability/generate` (TUTOR) and public
  `GET /tutors/{userId}/availability` (only `AVAILABLE` + future slots).
- Rule/slot **PRIMARY KEY for the tutor too** (`FindByIdForUpdate` → lock on
  the user row) — booking uses `SELECT … FOR UPDATE` on the slot to serialize
  concurrent buyers, and the DB unique constraint is the final backstop.

### 5.6 Booking & cancellation policy (`booking`)

`createBooking` (STUDENT, `@Transactional`):
1. Lock the slot row (`findByIdForUpdate`) — serializes racing buyers.
2. Reject if not `AVAILABLE`, or if `start_at` already passed.
3. `amount = hourlyRate × slot duration (rounded 2dp, HALF_UP)` — a snapshot
   of the tutor's rate at booking time.
4. Save booking (`CONFIRMED`), mark slot `BOOKED`, fire notification
   (student + tutor), attach payment info (null until paid).

`cancelBooking` (STUDENT, `@Transactional`):
- Only `CONFIRMED` and only **before** `start_at`.
- **Refund policy (24h window):** cancel > 24h before → **full refund**, $0 fee;
  cancel ≤ 24h before → **50% fee**, refund `amount − 50%`.
- Reopens the slot (`AVAILABLE`), triggers `PaymentService.refundForCancelIfPaid`
  (a paid booking is refunded via Stripe automatically), then cancels.

`completeExpiredBookings()` (called by scheduler, also callable directly):
- Finds `CONFIRMED` bookings whose slot **ended** in the past → `COMPLETED`,
  fires `SESSION_COMPLETED` notification to student **and** tutor.

`sendSessionReminders()`:
- For `CONFIRMED` bookings starting within the **next 24h** (and no
  `reminder_sent_at` yet): sets `reminderSentAt` (one-time guard) and fires
  `SESSION_REMINDER` notifications. Cancelled/far-future bookings are
  automatically excluded by the query predicate.

### 5.7 Payments & Stripe (`payment`)

Two trust boundaries — the frontend NEVER decides payment state:

1. **Checkout** `POST /bookings/{bookingId}/checkout` (STUDENT):
   - booking must be `CONFIRMED`; if `amount == 0` (free session) → returns
     `{ status: "PAID" }` immediately without Stripe.
   - Creates/reuses a `PENDING` Payment row, calls Stripe
     `checkout.sessions.create`, persists the session id **inside the same
     transaction** (this was a Phase 8 bug: writing the id outside the tx meant
     webhooks could never find it), returns the hosted checkout URL.
   - `UK booking_id` ⇒ one payment per booking; already-`PAID`/`REFUNDED` →
     409.
2. **Webhook** `POST /payment/webhook` (public, but signature-verified via
   `stripe.webhook-secret`; bad signature → 401 `PaymentSignatureException`):
   - `checkout.session.completed` → find payment by session id, PENDING → PAID
     (idempotent: non-PENDING → ignore duplicate), fire `PAYMENT_RECEIPT`
     notification.
   - `checkout.session.expired` → PENDING → FAILED, so the student can retry.
   - unfinished/bogus → ignore or 401.
3. **Refunds:** only ever triggered server-side:
   - cancel a paid booking → `PaymentService.refundForCancelIfPaid` calls
     Stripe refund with the calculated amount and sets
     `REFUNDED`/`PARTIALLY_REFUNDED` + fires `PAYMENT_REFUNDED`.
   - a completed webhook landing on a cancel-while-paying booking auto-refunds
     immediately.

All Stripe interaction lives behind the **`StripeGateway` interface**
(`StripeGatewayImpl` in prod, mocked in tests), so the payment domain is testable
without a Stripe account.

### 5.8 Notifications & the scheduler (`notification`, Phase 9)

- **Storage:** `notifications` (user_id CASCADE, booking_id SET NULL, 7 types,
  message, is_read). No recipient list — each notification row is a single
  recipient, so fan-out writes one row per party.
- **Fan-out wiring** (all fire-and-forget inside the business transaction):
  - `BookingService.createBooking` → in-app notif to **student** (`BOOKING_CONFIRMED`)
    + **tutor** (`NEW_BOOKING`)
  - `BookingService.cancelBooking` → both parties (`BOOKING_CANCELLED`)
  - `PaymentService` completed webhook → `PAYMENT_RECEIPT`; refund → `PAYMENT_REFUNDED`
  - `BookingService.completeExpiredBookings` → both parties (`SESSION_COMPLETED`)
  - `BookingService.sendSessionReminders` → `SESSION_REMINDER` (once)
  - `NotificationService` also composes the human-readable `message` and hands
    each message to `MailService` when mail is enabled.
- **REST:** `GET /notifications` (own, newest first), `GET
  /notifications/unread-count`, `POST /notifications/{id}/read`,
  `POST /notifications/read-all`. Cross-user access → 404.
- **`MailService`:** pure email wrapper. `mail.enabled` (default false);
  when disabled it never touches the `JavaMailSender`. Send failures are
  **swallowed and logged** — a broken SMTP server can never fail a booking or
  payment.
- **`SessionScheduler`** (`@EnableScheduling` on the app class):
  - `@Scheduled(fixedDelay = 1m)` → `completeExpiredBookings()`
  - `@Scheduled(fixedDelay = 5m)` → `sendSessionReminders()`
  - Gated by `app.scheduler.enabled` (`SCHEDULER_ENABLED`): `true` in prod,
    **false in tests** so the suite never depends on clock timing.

---

## 6. Frontend Deep Dive

### 6.1 Boot & routing

`main.tsx` mounts React + AppShell. `App.tsx` is the route table:

```
/login       Login           /register  Register (Student | Tutor)
/discover    tutor search    /tutors/:userId  public tutor profile
/tutor/setup  tutor profile   /tutor/availability  rules + generation
/bookings    student bookings + pay/cancel   /  home
```

Protected-route isn't centralized — each page reads session state and
redirects (simple, convention over framework).

### 6.2 The API client — `services/api.ts`

One Axios instance, `baseURL = VITE_API_BASE_URL ?? http://localhost:8080/api`.
A **request interceptor** reads `localStorage.accessToken` and attaches
`Authorization: Bearer …`, so services never manage the header. All services
(`auth.ts`, `tutors.ts`, `availability.ts`, `bookings.ts`, `notifications.ts`)
unwrap `ApiResponse` and throw on `success === false`.

### 6.3 Session storage

`services/auth.ts`: on login/register the token + user are written to
`localStorage` (`accessToken`, current user shape). `getStoredUser()` /
`clearSession()` feed the header bar; `AppShell` shows Sign out. (Storing the
JWT in `localStorage` is noted in Known Gaps §11 — httpOnly cookie would be
stricter.)

### 6.4 Key UI

- **AppShell:** sticky header (brand, desktop nav, **NotificationBell**, theme
  toggle, user name, sign out), mobile slide menu, footer.
- **NotificationBell** (Phase 9): bell with unread-count badge (polls
  `GET /notifications/unread-count` every 60s), dropdown listing
  `GET /notifications`, `POST /notifications/{id}/read` on click,
  `POST /notifications/read-all`; closes on outside click. Rendered only for
  logged-in users.
- **Discover:** tutor cards hit `GET /tutors` with search/filters + paging.
- **TutorAvailabilityPage:** CRUD rules, one-off slots, "generate slots" →
  `POST /tutors/availability/generate`.
- **MyBookingsPage:** each booking shows status + payment line; Pay CTA
  (CONFIRMED + unsettled) redirects to the Stripe hosted URL; Cancel button
  enforces the refund policy.

---

## 7. A Full Journey (all parts working together)

1. **Tutor registers** (`POST /auth/register`, role `TUTOR`) → token.
2. **Tutor sets profile + skills** (`PUT /tutors/profile`,
   `PUT /tutors/profile/skills`).
3. **Tutor defines rules** (`PUT /tutors/availability/rules`) and generates
   concrete `AVAILABLE` slots (`POST /tutors/availability/generate`).
4. **Student registers** (`role STUDENT`), browses `/discover`
   (`GET /tutors?skill=…`), opens a tutor profile
   (`GET /tutors/{userId}`), sees public slots
   (`GET /tutors/{userId}/availability`).
5. **Student books** `POST /bookings {slotId, message}` → slot locked
   (`FOR UPDATE`), booking `CONFIRMED`, slot `BOOKED`, student + tutor get
   in-app `BOOKING_CONFIRMED`/`NEW_BOOKING`. Price snapshot from tutor rate.
6. **Student pays** `POST /bookings/{id}/checkout` → Stripe hosted page; Stripe
   calls `POST /payment/webhook` → payment `PAID`, `PAYMENT_RECEIPT` notified.
7. **24h before start:** scheduler marks `reminder_sent_at` and notifies both
   parties. If student cancels earlier: full/partial refund per policy, slot
   reopens, both notified.
8. **After the slot ends:** scheduler flips booking to `COMPLETED` and notifies
   both parties.
9. **Parked for future phases**: email (already wired, disabled), chat (P10),
   video (P11), reviews (P12), analytics (P13).

---

## 8. Configuration & Environment Variables

Profiles: `local` (active by default → `application-local.yml` datasource →
localhost:5433), `test` (`application-test.yml` → H2 + mail/scheduler off).

| Variable | Default | Purpose |
|----------|---------|---------|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | localhost:5433 / postgres / password | JDBC |
| `JWT_SECRET` | — (required, ≥32 chars) | HS256 signing key |
| `JWT_EXPIRATION_MS` | 86400000 | token lifetime |
| `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`, `STRIPE_RETURN_URL` | — / — / localhost:5173/bookings | payments |
| `MAIL_HOST/PORT/USERNAME/PASSWORD` | — | SMTP |
| `MAIL_ENABLED` | false | actually send emails |
| `MAIL_HEALTH_ENABLED` | false | include SMTP probe in `/actuator/health` |
| `SCHEDULER_ENABLED` | true | session-completion + reminder jobs |
| `VITE_API_BASE_URL` | http://localhost:8080/api | frontend → backend |

**Why the mail health check is off by default:** adding the mail starter
auto-registered a mail health indicator; dev has no SMTP server, so health went
DOWN (503). It's opt-in via `MAIL_HEALTH_ENABLED`.

**Actuator:** `/actuator/health` + `/actuator/info` exposed and public.

---

## 9. How to Run (complete, copy-paste safe — zsh treats `#` as NOT a comment)

```bash
# 1) Database ─────────────────────────────────────────────
open -a Docker                      # wait for the daemon
docker compose up -d                # Postgres on :5433

# 2) Backend: JAVA_HOME and JWT_SECRET MUST be in the SAME shell ─────────────
cd backend
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
export JWT_SECRET="$(openssl rand -base64 48)"
mvn spring-boot:run                 # http://localhost:8080/api (Flyway migrates)

# 3) Frontend ─────────────────────────────────────────────
cd frontend
npm install                         # only once
npm run dev                         # http://localhost:5173
```

Health check: `curl http://localhost:8080/api/actuator/health` →
`{"status":"UP"}`.

Troubleshooting quick hits:
- **"Port 8080 already in use"** → another backend is running: `lsof -nP -iTCP:8080
  -sTCP:LISTEN`, kill it.
- **"JWT_SECRET must be…"** → env var was exported in a different terminal.
- **Postgres "uninitialized / password not specified"** → a stray container:
  `docker rm <name>` then `docker compose up -d` (never store data outside the
  compose volume).
- **`npm error EINVALIDTAGNAME`** → you pasted an inline `# comment`; zsh passes
  `#` to npm. Remove it.

---

## 10. Testing

```bash
cd backend
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
mvn test        # 128 tests, green (as of Phase 9)
```

- Profile `test` swaps Postgres for **H2** (create-drop) and disables the
  scheduler + mail → full integration tests with zero external services.
- Each package has `*IntegrationTest` (MockMvc + real Spring context +
  repositories) and unit tests (`JwtServiceTest`, `MailServiceTest`, …).
- Tests clean state per class (`notification → payment → booking → slot → rule →
  skill → user`), and rely on H2 FK cascade semantics. Stripe is mocked behind
  `StripeGateway`.
- Frontend: `npx tsc -b && npm run build` (strict type-check + production
  build) — no unit test framework configured yet.

---

## 11. Known Gaps & Security Notes (important!)

1. **`ADMIN` is self-registerable.** `POST /auth/register` accepts any role,
   including `ADMIN`, with no guard. Anyone can create an admin account. Fix:
   first-user bootstrap or `@PreAuthorize` around admin creation.
2. **No `FACULTY` role.** Only `STUDENT`, `TUTOR`, `ADMIN` exist. If "faculty"
   is needed as a separate identity, that's a new role + registration UI change.
3. **JWT in `localStorage`** — the frontend stores the token in localStorage,
   so it's readable by any XSS. An httpOnly cookie + CSRF story would be the
   production-grade upgrade (Phase 14 backlog).
4. **CORS is wide open** (all origins, credentials). Fine for dev; lock down
   to the real frontend origin in prod.
5. **Debug logging in prod config** — `logging.level.com.skillbook: DEBUG` and
   `org.hibernate.SQL: DEBUG` in `application.yml`; consider INFO for prod.
6. **Emails**: no templates yet (plain text `message`), no per-user email
   opt-out, no delivered-tracking.
7. **Notifications** have no pagination (full history returned) and only a
   client-side 60s poll (real-time push comes with websockets, Phase 10).
8. **Not applied to live DB** unless you run the backend: Flyway applies
   `V6__notification_tables.sql` (and any future migration) on startup.
9. **Stripe checkout edge**: a session created at Stripe whose DB commit fails
   after the HTTP call to Stripe (narrow window) → reconciliation job is Phase
   13 backlog.

---

## 12. Where Each Answer Lives (map)

| "How do I…?" | Look at |
|--------------|---------|
| find every endpoint + who may call it | `api-endpoints.txt`, controllers' `@PreAuthorize` |
| add a feature to bookings | `booking/BookingService.java`, `V4/V6` migrations |
| wire a new notification | `NotificationService.notifyX`, add `NotificationType` |
| change the refund policy | `BookingService` constants (`FULL_REFUND_WINDOW_HOURS`, `PARTIAL_FEE_RATE`) |
| change the reminder window | `BookingService.REMINDER_WINDOW_HOURS` |
| make search sort/score differently | `TutorSearchService.resolveSort` + `TutorProfileRepository.search` |
| add a role | `common/Role.java`, `AuthService.register`, `SecurityConfig`, `Register.tsx` |
| run tests / understand evidence | `.claude/tdd/phaseN-*.tdd.md` |
| understand a phase's decisions | `.claude/plan/phaseN-*.md` |

---

*Generated from source inspection — accurate as of Phase 9 (commit 77ac57f),
128-test suite green.*