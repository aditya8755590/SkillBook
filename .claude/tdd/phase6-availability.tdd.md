# TDD Evidence Report — Phase 6: Availability Management + Slot Generation

## Source plan
[`.claude/plan/phase6-availability.md`](../plan/phase6-availability.md)

## User journeys
- As a **tutor**, I want to define recurring weekly hours in my own timezone, so that students only ever see my real availability.
- As a **tutor**, I want to publish concrete bookable slots for the coming days, so that students can find and book me.
- As a **tutor**, I want to add/remove one-off slots outside my weekly rules, so that I can offer extra or temporary time.
- As a **visitor or student**, I want to view a tutor's available slots in a date range without logging in, so that I can decide whom to book.

## Task report

### 1. Weekly recurring rules — PUT/GET `/tutors/availability/rules` (TUTOR)
- Execution: `AvailabilityRule` entity (day_of_week ISO 1–7, LocalTime start/end, IANA timezone, active), `AvailabilityRuleRepository` (delete-by-tutor, ordered find, exist checks), `AvailabilityService.replaceRules` (delete + flush + batch insert, returns sorted), `AvailabilityRuleRequest/Response/RulesRequest` DTOs.
- Validation: `mvn test -Dtest=AvailabilityRuleIntegrationTest` → GREEN 7/7 (replace, clear-previous, day 0/8 → 400, end ≤ start → 400, invalid timezone → 400, STUDENT → 403, anonymous → 401).

### 2. One-off slots — POST/DELETE `/tutors/availability/slots` (TUTOR)
- Execution: `AvailabilitySlot` entity (OffsetDateTime startAt/endAt stored UTC, status AVAILABLE default, source ONE_OFF, optional rule FK), `addOneOffSlot` (duplicate `(tutor, startAt)` → 409), `deleteOneOffSlot` (404 if missing, 409 if BOOKED or not own).
- Validation: slot suite GREEN 9/9 (add+delete, delete-BOOKED → 409, duplicate-start → 409, generate, idempotent, timezone, public listing, unknown tutor 404, past-dates skipped).

### 3. Slot generation — POST `/tutors/availability/generate` (TUTOR)
- Execution: `generateSlots` materializes active weekly rules into concrete UTC `AvailabilitySlot`s for a `[startDate, endDate]` per-day range, converting `LocalDateTime.of(day, rule.startTime).atZone(ZoneId.of(rule.timezone)).toInstant()`; idempotent via the `(tutor_id, start_at)` unique constraint/exists check; skips past instants; returns `SlotGenerationResponse(generated, skipped, ...)`.

### 4. Public availability listing — GET `/tutors/{userId}/availability?from=&to=` (PUBLIC)
- Execution: service validates the target user is a TUTOR with a profile (else 404), finds `AVAILABLE` slots in `[from, to]` ordered by start, maps to UTC strings. `SecurityConfig` adds `.requestMatchers(GET, "/tutors/*/availability").permitAll()`; mutations stay `authenticated` + `@PreAuthorize("hasRole('TUTOR')")`.

### 5. Frontend `/tutor/availability` page
- Execution: `frontend/src/services/availability.ts` (typed client on the shared `api` instance) + `frontend/src/pages/TutorAvailabilityPage.tsx` (weekly-rule editor with add/remove rows and time pickers, "Save rules" replace, "Generate next 7 days", deletable slot list), route in `App.tsx`, TUTOR-only nav link in `AppShell.tsx`.
- Validation: `npx tsc -b && npm run build` → PASS (vite build clean).

### GREEN outcome
Full backend suite `mvn test`: **77 tests, 0 failures, 0 errors** (61 prior from phases 1–5 + 16 availability).

## Test specification

| # | What is guaranteed | Test file/command | Result | Evidence |
|---|--------------------|-------------------|--------|----------|
| 1 | PUT 2 rules → GET returns 2 sorted by day | AvailabilityRuleIntegrationTest | PASS | `mvn test` |
| 2 | PUT replace clears previous rules | AvailabilityRuleIntegrationTest | PASS | `mvn test` |
| 3 | day_of_week 0/8 rejected (400) | AvailabilityRuleIntegrationTest | PASS | `mvn test` |
| 4 | end ≤ start rejected (400) | AvailabilityRuleIntegrationTest | PASS | `mvn test` |
| 5 | invalid IANA timezone rejected (400) | AvailabilityRuleIntegrationTest | PASS | `mvn test` |
| 6 | STUDY role blocked from rules (403) | AvailabilityRuleIntegrationTest | PASS | `mvn test` |
| 7 | anonymous blocked from rules (401) | AvailabilityRuleIntegrationTest | PASS | `mvn test` |
| 8 | one-off slot added then deletable; reflected in public list | AvailabilitySlotIntegrationTest | PASS | `mvn test` |
| 9 | delete BOOKED one-off slot rejected (409) | AvailabilitySlotIntegrationTest | PASS | `mvn test` |
| 10 | duplicate (tutor,startAt) rejected (409) | AvailabilitySlotIntegrationTest | PASS | `mvn test` |
| 11 | generate from Mon 09:00–11:00 UTC yields one slot; off-days none | AvailabilitySlotIntegrationTest | PASS | `mvn test` |
| 12 | generation is idempotent (2nd run generated=0) | AvailabilitySlotIntegrationTest | PASS | `mvn test` |
| 13 | Asia/Kolkata Mon 09:00 → startAt 03:30Z (UTC+5:30 honored) | AvailabilitySlotIntegrationTest | PASS | `mvn test` |
| 14 | public lists availability without token (200) | AvailabilitySlotIntegrationTest | PASS | `mvn test` |
| 15 | unknown tutor → 404 | AvailabilitySlotIntegrationTest | PASS | `mvn test` |
| 16 | past dates skipped during generation | AvailabilitySlotIntegrationTest | PASS | `mvn test` |
| 17 | frontend compiles clean (tsc + vite build) | `npx tsc -b && npm run build` | PASS | terminal |

## Coverage and known gaps
- Coverage command not collected this phase (backend postgres/H2 integration-heavy via MockMvc; no coverage threshold plugin configured — documented in each prior phase report).
- H2 timezone quirk: `TIMESTAMPTZ` round-trips through the JVM default zone, so the suite runs with surefire `<argLine>-Duser.timezone=UTC</argLine>` (pom.xml) to keep assertions deterministic regardless of host TZ. `hibernate.jdbc.time_zone: UTC` (application-test.yml) retained but alone insufficient.
- Live PostgreSQL verification **skipped by user choice** — `V3__availability_tables.sql` is written and reviewed but not yet applied to the running Docker Postgres (requires backend restart, a forbidden-category action pending approval). Schema behavior is covered by H2 + Flyway-disabled test profile; real-PG apply remains as follow-up.
- Test context is shared, so `@BeforeEach` clears `availability_slots` → `availability_rules` → `skill_offered` → `users` (FK dependency order already exercised in Phase 5).
- Booked-status transitions and session-duration config deferred to Phase 7.

## Merge evidence (RED/GREEN summary)
- RED (compile-time): tests referenced `AvailabilitySlotRepository.findOneBy...` / `AvailabilityService` before classes existed → `mvn test-compile` failed on missing symbols (intended RED signal).
- GREEN: implementation completed; `mvn test` → **77/77 PASS** (incl. the two timezone-intolerant generation tests, fixed via `-Duser.timezone=UTC` + `OffsetDateTime` UTC-normalization).
- Frontend GREEN: `npx tsc -b && npm run build` clean.
- If checkpoint commits are squashed for the Phase 6 PR, carry the preceding RED/GREEN summary and this table into the PR body.