# Implementation Plan: Phase 6 — Availability Management + Slot Generation

## Task Type
- [x] Weekly recurring availability rules (tutor-managed)
- [x] One-off concrete slots (temporary availability)
- [x] Slot generation: materialize weekly rules into concrete UTC slots
- [x] Public availability listing per tutor (consumed by discovery/booking in Phase 7)
- [x] Frontend: availability manager page for tutors (`/tutor/availability`)

## Routes

| Method | Path | Role | Purpose |
|--------|------|------|---------|
| PUT | `/api/tutors/availability/rules` | TUTOR | Replace all of the tutor's weekly rules (idempotent replace) |
| GET | `/api/tutors/availability/rules` | TUTOR | List own weekly rules |
| POST | `/api/tutors/availability/slots` | TUTOR | Add a one-off concrete slot (`startAt`/`endAt`, ISO-8601 with offset) |
| DELETE | `/api/tutors/availability/slots/{id}` | TUTOR | Delete an own, still-AVAILABLE one-off slot |
| POST | `/api/tutors/availability/generate` | TUTOR | Materialize slots from active weekly rules for a `[startDate, endDate]` range |
| GET | `/api/tutors/{userId}/availability?from=&to=` | PUBLIC | List a tutor's concrete AVAILABLE slots in a UTC range |

All mutation endpoints return `{ success, data }` via the existing `ApiResponse` wrapper; error handling via `GlobalExceptionHandler`.

## New Flyway migration: `V3__availability_tables.sql`

### `availability_rules` (recurring weekly)
- `id UUID PK DEFAULT gen_random_uuid()`
- `tutor_id UUID NOT NULL FK → users(id) ON DELETE CASCADE`
- `day_of_week SMALLINT NOT NULL` — ISO: 1=Mon … 7=Sun (`LocalDate.getDayOfWeek().getValue()`)
- `start_time TIME NOT NULL` — tutor-local wall-clock
- `end_time TIME NOT NULL`
- `timezone VARCHAR(64) NOT NULL` — IANA id, e.g. `Asia/Kolkata`
- `active BOOLEAN NOT NULL DEFAULT TRUE`
- `created_at`, `updated_at` (BaseEntity convention)
- `UNIQUE (tutor_id, day_of_week, start_time)` — no duplicate rule for same weekday/start
- `CHECK (end_time > start_time)` — core validation at the DB level

### `availability_slots` (concrete, materialized, UTC)
- `id UUID PK DEFAULT gen_random_uuid()`
- `tutor_id UUID NOT NULL FK → users(id) ON DELETE CASCADE`
- `start_at TIMESTAMPTZ NOT NULL` — UTC instant
- `end_at TIMESTAMPTZ NOT NULL`
- `status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE'` — `AVAILABLE` now; `BOOKED` arrives with Phase 7 bookings
- `source VARCHAR(20) NOT NULL` — `RULE` (generated from a weekly rule) | `ONE_OFF` (manual)
- `rule_id UUID NULL FK → availability_rules(id) ON DELETE CASCADE` — provenance for RULE slots
- `created_at`, `updated_at`
- `UNIQUE (tutor_id, start_at)` — the DB-level guard Phase 7 booking will lock against (prevents double-generation and, later, double-booking)
- `CHECK (end_at > start_at)`
- `INDEX idx_availability_slots_tutor_start (tutor_id, start_at)`
- `INDEX idx_availability_slots_tutor_status (tutor_id, status)`

> **Timezone model**: rules store the tutor's local wall-clock + IANA timezone; generation converts to UTC instants and persists them as `TIMESTAMPTZ`. This satisfies the Phase 6 "timezone-safe scheduling" engineering goal and keeps Phase 7 comparisons in one timezone (UTC).

## Decisions

### 1. Replace semantics for weekly rules
`PUT /tutors/availability/rules` with `{ "rules": [...] }`:
- Delete all existing rules for the tutor, insert the new ones
- `day_of_week` must be 1–7; `start_time`/`end_time` as `"HH:mm"`; timezone must parse via `ZoneId.of` (else 400)
- Return the new `List<AvailabilityRuleResponse>` sorted by day, then start

### 2. Materialized slots, generated from rules
`POST /tutors/availability/generate` with `{ "startDate": "2026-10-01", "endDate": "2026-10-07" }`:
- For each day in range, for each active rule with matching `day_of_week`:
  - `startUtc = LocalDateTime.of(day, rule.startTime).atZone(ZoneId.of(rule.timezone)).toInstant()`
  - `endUtc = ... endTime ...`
- Insert only if a slot at `(tutor_id, startAt)` does not already exist (idempotent — a second run creates nothing new)
- Returns `SlotGenerationResponse(generated, skipped, startDate, endDate)`
- Skips dates already past (startUtc before now) without error

### 3. One-off slots
`POST /tutors/availability/slots` inserts a single concrete slot marked `source=ONE_OFF`. `DELETE` removes only an own slot whose status is `AVAILABLE` (409 if `BOOKED`). Used for ad-hoc free time outside weekly rules.

### 4. Public listing
`GET /tutors/{userId}/availability?from=&to=` (both ISO-8601 UTC instants, required) returns only `status=AVAILABLE` slots in `[from, to]`, ordered by start. 404 if the tutor has no profile. This is the feed Phase 7 booking (and later, the student UI) reads.

### 5. No `BaseEntity` change — entities follow existing pattern
Both entities extend `BaseEntity` (UUID PK, createdAt/updatedAt). `@Entity` + `@Table` + Lombok `@Getter/@Setter` matching `SkillOffered`/`TutorProfile` style. `time`/`timestamptz` columns map to `LocalTime`/`Instant`.

### 6. Security additions
- `SecurityConfig.requestMatchers(GET, "/tutors/*/availability")` → `permitAll` (one extra segment; distinct from existing `/tutors/*`)
- All `/tutors/availability/**` mutation URLs are `authenticated`; role enforced via `@PreAuthorize("hasRole('TUTOR')")` method security (mirrors Phase 5 pattern)

## Domain files created (package `com.skillbook.availability`)
- `AvailabilityRule.java`, `AvailabilitySlot.java` (entities)
- `AvailabilityRuleRepository.java` (`deleteByTutorId`, `findByTutorIdOrderByDayOfWeekAscStartTimeAsc`, `exists` checks)
- `AvailabilitySlotRepository.java` (`deleteByTutorIdAndStatus`, `findByTutorIdAndStatusAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc`, `existsByTutorIdAndStartAt`, `deleteByIdAndTutorIdAndStatus`)
- `AvailabilityService.java`, `AvailabilityRuleService.java` or single service (decided at implementation; single `AvailabilityService` likely)
- `AvailabilityController.java`
- `dto/AvailabilityRuleRequest.java`, `dto/AvailabilityRuleResponse.java`, `dto/AvailabilityRulesRequest.java`
- `dto/OneOffSlotRequest.java`, `dto/AvailabilitySlotResponse.java`
- `dto/SlotGenerationRequest.java`, `dto/SlotGenerationResponse.java`

## Test cases (RED first)
### AvailabilityRuleIntegrationTest
1. `replaceWeeklyRulesAndList` — PUT 2 rules → GET returns 2, sorted by day
2. `replaceRulesClearsPrevious` — PUT [Mon,Thu], then PUT [Tue] → only Tue remains
3. `invalidDayOfWeekRejected` — 0 and 8 → 400
4. `invalidTimeRangeRejected` — end ≤ start → 400
5. `invalidTimezoneRejected` — `Not/AZone` → 400
6. `studentCannotManageRules` → 403
7. `unauthenticatedCannotManageRules` → 401

### AvailabilitySlotIntegrationTest
8. `addAndDeleteOneOffSlot` — POST slot → public list shows it; DELETE → gone
9. `deleteBookedSlotRejected` — slot with status BOOKED (set via repo) → DELETE → 409
10. `duplicateSlotAtSameStartRejected` — second slot, same `(tutor, startAt)` → 409 (unique constraint)
11. `generateSlotsFromWeeklyRule` — rule Mon 09:00–11:00 UTC, generate that Monday → exactly one slot; off-days unaffected
12. `generateIsIdempotent` — run same range twice → second run `generated=0`
13. `generateHonorsTimezone` — rule in `Asia/Kolkata` Mon 09:00 → generated `startAt` UTC == `07:30Z` (IST = UTC+5:30)
14. `publicCanListAvailabilityWithoutToken` → 200
15. `unknownTutorReturns404` → 404
16. `pastDatesSkippedDuringGeneration` — range includes past days → no past slots created

## Files changed
- `backend/src/main/resources/db/migration/V3__availability_tables.sql`
- `backend/src/main/java/com/skillbook/config/SecurityConfig.java` (one permitAll matcher)
- New `availability` package (entities, repos, service, controller, DTOs)
- `frontend/src/types/index.ts`, `frontend/src/services/availability.ts`
- `frontend/src/pages/TutorAvailabilityPage.tsx`, route in `App.tsx`, nav link in `AppShell.tsx`

## Frontend scope (minimal, consistent with existing polish)
- `/tutor/availability` page: list weekly rules with day/start/end/timezone inputs (toggle active), "Add rule" rows, save via PUT replace; show generated slot counts for next 7 days after generation; one-off slot quick-add form
- Linked from AppShell "My Profile" dropdown / nav as "Availability" (visible to TUTORs only)

## Verification
```bash
# Backend (JAVA_HOME must be temurin-21 — JDK 26 breaks Lombok)
cd backend
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
mvn test                      # all suites incl. V3, expects ~77+ tests
mvn spring-boot:run           # start new instance (see Migration note below)

# Live checks against Docker PG
curl -s "http://localhost:8080/api/tutors/<uid>/availability?from=...&to=..."
# PUT rules as a tutor → generate → GET availability → confirm slots

# Frontend
cd frontend && npx tsc -b && npm run build
```

## Migration note (requires explicit permission)
Creating `V3__availability_tables.sql` is part of implementation, but **applying it** happens automatically when the backend restarts (Flyway runs at startup). Per `rules.md`, restarting the backend applies a migration and is a forbidden-category action — I will ask for approval to restart before the green-phase live verification.

## Deferred
- Empty-state logic that hides tutors with no generated availability from discovery — used by discovery ranking (Phase 5 deferred note; revisit with Phase 7 booking)
- Booking/occupying slots, `BOOKED` status transitions — Phase 7
- Slot duration/session-length configuration, timezone editor on the tutor profile — Phase 7/9