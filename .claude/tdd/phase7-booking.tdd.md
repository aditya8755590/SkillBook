# Phase 7 — Concurrency-Safe Booking: TDD Evidence Report

**Branch:** `main` (worktree; Phase 7 backend + frontend uncommitted at time of writing)
**Plan:** `.claude/plan/phase7-booking.md`

## User journeys

- As a student, I want to book an available slot on a tutor's profile so that my session is confirmed at the tutor's current hourly rate.
- As a student, I want to see my bookings and cancel them so that I can reclaim the slot, subject to the 24h refund policy.
- As a platform, I want double-booking to be impossible even under concurrency so that a slot is never sold twice.

## Task report

| Task | Execution summary | Validation command | Result |
|------|-------------------|--------------------|--------|
| Slot row-lock + `UNIQUE(slot_id)` backstop | Implemented `findByIdForUpdate` via native `SELECT … FOR UPDATE` (PG + H2 compatible) plus `UK bookings(slot_id)` | `mvn test` | PASS (all 94) |
| Booking state machine (`CONFIRMED` → `CANCELLED`/`COMPLETED`) | `BookingService.cancelBooking` + `completeExpiredBookings` implemented and exercised end-to-end | `mvn test` | PASS |
| 24h cancellation policy (full/50% refund) | Fee/refund computed and stored on cancel; verified at +30h and +12h | `mvn test` | PASS |
| Student booking endpoints (CRUD + cancel) | `BookingController` with `@PreAuthorize("hasRole('STUDENT')")`, owner-reads → 404 | `mvn test` | PASS |
| Phase 8 payment hook points | `amount`/`currency`/`refund_amount`/`cancellation_fee` stored on `bookings`; no Stripe code | `mvn test` + migration review | PASS |
| Frontend `/bookings` (list + cancel) | `MyBookingsPage` + `/bookings` route + STUDENT nav link | `npx tsc -b && npm run build` | PASS |
| Book action on tutor profile | "Available times" section reuses `fetchAvailability`; 409 → "This slot was just booked — pick another" | `npx tsc -b && npm run build` | PASS |

## Bugs found and fixed during RED verification

1. **`FOR NO KEY UPDATE` on H2.** Hibernate's `@Lock(PESSIMISTIC_WRITE)` on a JPQL query emits `FOR NO KEY UPDATE`, which H2 (`MODE=POSTGRESQL`) cannot parse → every `POST /bookings` 500'd. Fix: native query `select * from availability_slots where id = :id for update` and removed `@Lock` (Spring Data rejects `@Lock` on native queries). Verified: the same query runs on H2 in tests and emits plain `FOR UPDATE` on PG — a stronger but valid lock for the race.
2. **Shared-context FK-order cleanup.** With `bookings` → `availability_slots` → `availability_rules` now referencing `users`, test classes that cleared only `users` failed with `23503` when they ran after booking/availability tests left rows. Fix: added `bookingRepository`/`availabilitySlotRepository`/`availabilityRuleRepository` clears before `userRepository.deleteAll()` in `StudentProfileIntegrationTest`, `TutorProfileIntegrationTest`, `AuthFlowIntegrationTest`, and the four skill test classes (matching the pattern already applied to the two availability integration tests).

## Test specification

| # | What is guaranteed | Test | Type | Result | Evidence |
|---|--------------------|------|------|--------|----------|
| 1 | Student can book a future AVAILABLE slot → 201 CONFIRMED, amount = rate×duration, slot flips to BOOKED | `createBookingConfirmsAndMarksSlotBooked` | integration | PASS | `mvn test` |
| 2 | Second booking on the same slot → 409 | `doubleBookSameSlotRejected` | integration | PASS | `mvn test` |
| 3 | Unknown slot → 404 | `missingSlotReturns404` | integration | PASS | `mvn test` |
| 4 | Tutor cannot create bookings (403) | `tutorCannotCreateBooking` | integration | PASS | `mvn test` |
| 5 | Anonymous request → 401 | `anonymousCannotCreateBooking` | integration | PASS | `mvn test` |
| 6 | List returns only the caller's bookings, newest first | `listMyBookingsReturnsOwnOnly` | integration | PASS | `mvn test` |
| 7 | Owner can fetch booking by id (>24h cancel → full refund) | `getBookingByIdReturnsOwn` | integration | PASS | `mvn test` |
| 8 | Non-owner fetch → 404 (existence hidden) | `getBookingByIdNonOwnerReturns404` | integration | PASS | `mvn test` |
| 9 | Cancel ≥24h → 200, fee 0, refund = amount, slot freed & visible in public feed | `cancelBookingBeyond24hFullRefundAndFreesSlot` | integration | PASS | `mvn test` |
| 10 | Cancel <24h → fee 50%, refund 50% | `cancelBookingWithin24hPartialRefund` | integration | PASS | `mvn test` |
| 11 | Cancel after slot start → 409 | `cancelBookingAfterSlotStartRejected` | integration | PASS | `mvn test` |
| 12 | Double cancel → 409 | `cancelBookingTwiceRejected` | integration | PASS | `mvn test` |
| 13 | Cancel of COMPLETED → 409 | `cancelCompletedBookingRejected` | integration | PASS | `mvn test` |
| 14 | Non-owner cancel → 404 | `nonOwnerCannotCancelBooking` | integration | PASS | `mvn test` |
| 15 | Tutor cancel → 403 | `tutorCannotCancelBooking` | integration | PASS | `mvn test` |
| 16 | CONFIRMED → COMPLETED when slot ends | `completedTransitionWhenSessionPasses` | integration | PASS | `mvn test` |
| 17 | DB `UNIQUE(slot_id)` backstop rejects a second booking row at repo level | `uniqueSlotConstraintBackstop` | integration | PASS | `mvn test` |
| 18 | Frontend booking list renders CONFIRMED/CANCELLED/COMPLETED with refunds | `MyBookingsPage` + `build` | E2E-style | PASS (build+typecheck) | `npx tsc -b && npm run build` |

## Coverage and known gaps

- Full suite: **94/94 tests PASS** (`mvn test`, H2 `MODE=POSTGRESQL`, Flyway off). No coverage threshold configured for this project; unit-style JPA entity tests plus 17 booking integration cases cover the new domain.
- A true two-thread race is **not** exercised on H2 (H2 cannot run two real concurrent transactions). The invariant is proven sequentially (test 2) and the constraint proven directly (test 17); the live two-thread race is deferred to real-PG verification (plan Decision 2).
- Frontend booking treated as build/typecheck-verified; no Playwright spec exists in this repo yet (E2E coverage lands in Phase 14).

## Merge evidence

RED → GREEN:
- RED: `mvn test` initially failed — 12 booking failures + 3 FK errors (see "Bugs found and fixed" above).
- GREEN: after native `FOR UPDATE` fix and FK-order test cleanup, `mvn test` → `Tests run: 94, Failures: 0, Errors: 0` and `BUILD SUCCESS`.
- Refactor: none beyond the minimal fixes above; code split into many small files per repo convention.