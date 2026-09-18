# Implementation Plan: Phase 7 — Concurrency-Safe Booking

## Task Type
- [x] Race-safe booking: PG row-level lock (`SELECT … FOR UPDATE`) + `UNIQUE (slot_id)` backstop → one winner per slot
- [x] Booking state machine: `CONFIRMED` → `CANCELLED` | `COMPLETED` (terminal states; cancel by owner student only)
- [x] Cancellation policy: cancel ≥24h before start = full refund / no fee; <24h = 50% fee / 50% refund; after start = rejected
- [x] Student booking endpoints: create / list mine / get by id / cancel
- [x] Phase 8 payment hook point: `amount`/`currency`/`refund_amount`/`cancellation_fee` stored on the booking now; **no Stripe code**
- [x] Frontend (minimal): `/bookings` page (list + cancel) for STUDENT; book action on tutor profile availability

## Routes

| Method | Path | Role | Purpose |
|--------|------|------|---------|
| POST | `/api/bookings` | STUDENT | Create a booking for one AVAILABLE slot (`{ slotId, message? }`) → **201** |
| GET | `/api/bookings` | OWNER (STUDENT) | List the caller's bookings, newest first |
| GET | `/api/bookings/{id}` | OWNER (STUDENT) | Fetch one booking; 404 if not owned (existence hidden) |
| POST | `/api/bookings/{id}/cancel` | OWNER (STUDENT) | Cancel: free the slot, store refund/fee per 24h policy |

All responses via the existing `ApiResponse` wrapper, errors via `GlobalExceptionHandler`.
Status code map: **201** create · **200** ok · **400** validation / past slot / tutor has no rate · **401** anonymous · **403** wrong role (tutor can neither create nor cancel) · **404** unknown slot / unknown booking / non-owner · **409** already-booked double-book, `slot_id` unique violation, already CANCELLED/COMPLETED, or session already started.

## New Flyway migration: `V4__booking_tables.sql`

### `bookings`
- `id UUID PK DEFAULT gen_random_uuid()`
- `student_id UUID NOT NULL FK → users(id) ON DELETE CASCADE`
- `slot_id UUID NOT NULL FK → availability_slots(id) ON DELETE RESTRICT`
- `status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED'` — `CONFIRMED` | `CANCELLED` | `COMPLETED`
- `message VARCHAR(500) NULL` — optional student note
- `amount NUMERIC(8,2) NOT NULL` — snapshot: hourly_rate × slot duration (Phase 8 charges this)
- `currency VARCHAR(3) NOT NULL DEFAULT 'USD'` — `CHECK (currency IN ('USD'))`
- `refund_amount NUMERIC(8,2) NULL`, `cancellation_fee NUMERIC(8,2) NULL` — computed at cancel time, stored so Phase 8 executes refunds with **no migration**
- `cancelled_at TIMESTAMPTZ NULL`, `cancelled_by UUID NULL FK → users(id) ON DELETE SET NULL`
- `completed_at TIMESTAMPTZ NULL`
- `created_at`, `updated_at` (BaseEntity convention)
- `UNIQUE (slot_id)` — **the double-booking backstop**: at most one booking row per slot, enforced by the DB even if a code path skips the lock
- `CHECK (amount >= 0)`
- `INDEX idx_bookings_student (student_id)`, `INDEX idx_bookings_slot (slot_id)`, `INDEX idx_bookings_status (status)`

> `ON DELETE RESTRICT` on `slot_id` is deliberate: booking history must survive. Booked slots can't be deleted anyway (Phase 6 409), and a freed (CANCELLED) slot keeps its history for refunds, reviews (Phase 12), and analytics (Phase 13).

## Decisions

### 1. Row-level lock is the primary concurrency primitive
Add to `AvailabilitySlotRepository`: `@Lock(LockModeType.PESSIMISTIC_WRITE) Optional<AvailabilitySlot> findByIdForUpdate(UUID id)` — Hibernate emits `SELECT … FOR UPDATE` on PostgreSQL (accepted by H2 in `MODE=POSTGRESQL`, so the same path runs in tests). Race behavior: T1 locks the slot row → checks `AVAILABLE` → inserts booking + flips slot to `BOOKED` → commits; T2 blocks on the row lock, then reads the now-`BOOKED` row and throws `IllegalStateException` → **409 for the loser**. The DB `UNIQUE (slot_id)` plus the Phase 6 `UNIQUE (tutor_id, start_at)` remain backstops for any unlocked path; a `DataIntegrityViolationException` is mapped to **409** by a small new handler in `GlobalExceptionHandler` (generic message, no SQL detail leaked).

### 2. How the tests prove race-safety without real PG concurrency
H2 can't run two real concurrent transactions, so the suite proves the *invariant* sequentially and the *backstop* directly:
- Sequential double-book: first POST → 201; second POST on the same slot → 409 (status check on the locked read). Proves only one booking can exist per slot.
- Repository-level backstop: inserting a second booking row for the same `slot_id` directly (bypassing the service) throws `DataIntegrityViolationException` — proves the DB constraint catches what the service check would miss.
- A true two-thread race is guaranteed by design (lock + unique constraint) and exercised later in live-PG verification only.

### 3. Booking insert + slot flip are one atomic transaction
`createBooking` is a single `@Transactional` method: insert the booking row **and** set `slot.status = BOOKED` (dirty-checking flushes both on commit; any failure rolls back both). The `UNIQUE (slot_id)` constraint means the booking insert is the commit point that makes the second concurrent request fail — the slot UPDATE alone is never the guard.

### 4. State machine
- `CONFIRMED → CANCELLED`: only the owning student, only while `now < slot.startAt`; slot is freed (see #5). After startAt → 409 (Phase 11 will own no-shows).
- `CONFIRMED → COMPLETED`: automatic when `slot.endAt` passes — `BookingService.completeExpiredBookings()` flips `CONFIRMED` bookings whose slot has ended to `COMPLETED` (batched, idempotent). Scheduled triggering is deferred to Phase 9/11 (when sessions/notifications exist); the transition itself is implemented and tested now.
- `CANCELLED` and `COMPLETED` are terminal — any further transition → 409. Tutor cannot cancel: role gate (403) + owner check (404) make it impossible in every path.

### 5. CANCELLED slots are freed (status → AVAILABLE)
On cancel, `slot.status` returns to `AVAILABLE` — the slot reappears in the public availability feed (`GET /tutors/{id}/availability` filters `AVAILABLE` only) and can be re-booked. The CANCELLED booking row stays for history, refund execution, and analytics. Staying `BOOKED` would permanently destroy sellable capacity for a student-initiated cancel with no offsetting benefit.

### 6. Cancellation fee / refund model
With `now = OffsetDateTime.now(UTC)` and `start = slot.startAt`:
- `now.isBefore(start.minusHours(24))` → **full refund**: `cancellationFee = 0`, `refundAmount = amount`
- `now >= start.minusHours(24)` and `now < start` → **partial**: `cancellationFee = amount × 50%`, `refundAmount = amount − fee` (`HALF_UP`, 2dp)
- `now >= start` → **409** (cannot cancel a started session)
Both computed values are stored on the booking row at cancel time — Phase 8 reads them to execute the actual Stripe refund with zero recomputation and no schema migration.

### 7. Amount snapshot = hourly rate × slot duration
`amount = tutorProfile.hourlyRate × hours(slot)` (2dp, `HALF_UP`), read from `tutor_profiles` and captured **at booking time** so later rate changes never affect an existing booking; `currency = USD`. Tutor without a profile → 400 `IllegalArgumentException` ("Tutor has not set an hourly rate"). A 0.00 rate is valid (free session → Phase 8 skips payment). Requires a small repo addition: `TutorProfileRepository.findByUser_Id(UUID)`.

### 8. Owner-only reads/cancels → 404, not 403
A non-owner student fetching or cancelling a booking gets `ResourceNotFoundException` → **404** (existence hidden; bookings are private data). This deliberately differs from Phase 6's 409 "slot not owned" convention because slot existence is public while bookings are private. Tutors are stopped earlier by the `@PreAuthorize("hasRole('STUDENT')")` role gate → 403 before any owner logic runs.

### 9. Self-booking is structurally impossible — role gate covers it
Roles are exclusive single values (`Role.STUDENT | TUTOR | ADMIN`), so a tutor **cannot** book their own slot: `POST /bookings` requires `hasRole('STUDENT')` → a TUTOR token gets 403 at the gate. No service-level self-check is needed today; if dual-role users ever arrive, the guard belongs on `createBooking` (student.userId == slot.tutor.id → 400).

### 10. COMPLETED has no API endpoint in Phase 7
`completeExpiredBookings()` is a service method tested through the application context; wiring it to a scheduled job or to Phase 11's session lifecycle is deferred. `GET /bookings` still surfaces past `CONFIRMED` bookings (frontend labels them "Past session") until Phase 11 triggers completion.

## Domain files created (package `com.skillbook.booking`)
- `Booking.java` (entity — `@Table(name = "bookings", uniqueConstraints = @UniqueConstraint(name = "uk_bookings_slot", columnNames = "slot_id"))`, extends `BaseEntity`, `@ManyToOne` student/slot/cancelledBy, `@Enumerated(STRING)` status, `BigDecimal` money fields)
- `BookingStatus.java` (`CONFIRMED`, `CANCELLED`, `COMPLETED`)
- `BookingRepository.java` (`findAllByStudentIdOrderByCreatedAtDesc`, `findByIdAndStudentId`, `findByStatusAndSlot_EndAtBefore`)
- `BookingService.java` (`createBooking`, `listMyBookings`, `getBooking`, `cancelBooking`, `completeExpiredBookings`)
- `BookingController.java` (`@RequestMapping("/bookings")`, `@PreAuthorize("hasRole('STUDENT')")` + `Authentication.getName()` per Phase 6 pattern)
- `dto/BookingCreateRequest.java` (`@NotNull UUID slotId`, `@Size(max = 500) String message`)
- `dto/BookingResponse.java` (id, status, message, amount, currency, refundAmount, cancellationFee, cancelledAt, completedAt, createdAt, nested `slot`, `student`, `tutor`)
- `dto/BookingSlotInfo.java`, `dto/BookingStudentInfo.java`, `dto/BookingTutorInfo.java` (small records, consistent with the many-small-files convention)

Modified: `AvailabilitySlotRepository.java` (+ `findByIdForUpdate` with `@Lock(PESSIMISTIC_WRITE)`, `org.springframework.data.jpa.repository.Lock` / `jakarta.persistence.LockModeType`), `TutorProfileRepository.java` (+ `findByUser_Id`), `GlobalExceptionHandler.java` (+ `DataIntegrityViolationException` → 409, generic message).

## Test cases (RED first)
`BookingIntegrationTest` (MockMvc + `@SpringBootTest` + H2 `MODE=POSTGRESQL`, Flyway off, shared context). Two fixed users: **alice@example.com (TUTOR, hourly rate 40.00 via `PUT /tutors/profile`)**, **bob@example.com (STUDENT)**, both `password123`; a third user `carol@example.com (STUDENT)` is registered only in isolation tests. `@BeforeEach` clears in FK order: `bookingRepository.deleteAll()` → `availabilitySlotRepository.deleteAll()` → `availabilityRuleRepository.deleteAll()` → `skillOfferedRepository.deleteAll()` → `userRepository.deleteAll()`. Slots are created via the Phase 6 one-off endpoint with **now-relative** ISO instants (the 24h tests need dynamic times). Helpers: `registerAndLoginTutor/Student`, `createSlot(token, startIso, endIso)`, `createBooking(token, slotId)`.

1. `createBookingConfirmsAndMarksSlotBooked` — bob books alice's future 1h slot → 201, `status=CONFIRMED`, `amount=40.00`, `currency=USD`; slot status is `BOOKED` in the repo
2. `doubleBookSameSlotRejected` — bob books the same slot twice → first 201, second **409**
3. `missingSlotReturns404` — random UUID slot → **404**
4. `tutorCannotCreateBooking` — alice (TUTOR) POSTs `/bookings` → **403** (also proves a tutor can never book their own slot — Decision 9)
5. `anonymousCannotCreateBooking` — no token → **401**
6. `listMyBookingsReturnsOwnOnly` — bob books slot A; carol books slot B; bob's `GET /bookings` → only slot A, newest first
7. `getBookingByIdReturnsOwn` — bob `GET /bookings/{id}` → 200 with slot + tutor info
8. `getBookingByIdNonOwnerReturns404` — carol fetches bob's booking → **404** (existence hidden)
9. `cancelBookingBeyond24hFullRefundAndFreesSlot` — slot starts in 30h → cancel → 200, `status=CANCELLED`, `cancellationFee=0`, `refundAmount=40.00`, `cancelledAt` set; slot is `AVAILABLE` again and visible in the public availability feed
10. `cancelBookingWithin24hPartialRefund` — slot starts in 12h → cancel → `cancellationFee=20.00`, `refundAmount=20.00` (50% each)
11. `cancelBookingAfterSlotStartRejected` — booking planted via repo on a slot already started → cancel → **409**
12. `cancelBookingTwiceRejected` — first cancel 200; second cancel → **409**
13. `cancelCompletedBookingRejected` — booking moved to COMPLETED via repo, then cancel → **409**
14. `nonOwnerCannotCancelBooking` — carol cancels bob's booking → **404**
15. `tutorCannotCancelBooking` — alice (TUTOR) cancels bob's booking → **403**
16. `completedTransitionWhenSessionPasses` — booking on a past slot via repo → call `bookingService.completeExpiredBookings()` → `GET /bookings/{id}` shows `COMPLETED` with `completedAt`
17. `uniqueSlotConstraintBackstop` *(repository-level, not MockMvc)* — second booking row for the same `slot_id` inserted directly via `BookingRepository.save` throws `DataIntegrityViolationException`

Both Phase 6 availability test classes (`AvailabilityRuleIntegrationTest`, `AvailabilitySlotIntegrationTest`) get the booking-clear line added to `@BeforeEach` — once `bookings` exists, deleting slots first would violate the FK (Hibernate test DDL uses default FK semantics, not the migration's `ON DELETE`).

## Files changed
- `backend/src/main/resources/db/migration/V4__booking_tables.sql`
- `backend/src/main/java/com/skillbook/availability/AvailabilitySlotRepository.java` (+ `findByIdForUpdate` lock)
- `backend/src/main/java/com/skillbook/tutor/TutorProfileRepository.java` (+ `findByUser_Id`)
- `backend/src/main/java/com/skillbook/exception/GlobalExceptionHandler.java` (+ `DataIntegrityViolationException` → 409)
- New `booking` package (entity, enum, repo, service, controller, DTOs)
- `backend/src/test/java/com/skillbook/availability/AvailabilityRuleIntegrationTest.java` and `AvailabilitySlotIntegrationTest.java` (@BeforeEach FK-order fix)
- New `backend/src/test/java/com/skillbook/booking/BookingIntegrationTest.java`
- `frontend/src/types/index.ts`, new `frontend/src/services/bookings.ts`, new `frontend/src/pages/MyBookingsPage.tsx`
- `frontend/src/App.tsx` (`/bookings` route), `frontend/src/components/AppShell.tsx` (nav link for STUDENT), `frontend/src/pages/TutorProfilePage.tsx` (book action — available-times section)

## Frontend scope (minimal, consistent with existing polish)
- `/bookings` page (STUDENT-only, guarded like `TutorAvailabilityPage`): list own bookings — tutor name, local date/time, amount, status badge (CONFIRMED green / CANCELLED muted / COMPLETED blue), refund line when cancelled; "Cancel" button on CONFIRMED future bookings → `POST /bookings/{id}/cancel` → refresh; `Button`/`Alert`/`Spinner` from `frontend/src/components/ui/`
- Book action: on `TutorProfilePage`, for STUDENT viewers add an "Available times" section (reuses `fetchAvailability` from `services/availability.ts`, next-30-days window) with a per-slot "Book" button → `POST /bookings` → success `Alert` + navigate to `/bookings`; 409 maps to "This slot was just booked — pick another"
- Nav link in `AppShell` — "My Bookings" (`/bookings`) visible to `role === 'STUDENT'` only
- Types in `frontend/src/types/index.ts`: `Booking`, `BookingSlotInfo`, `BookingTutorInfo`, `BookingCreatePayload` (payment not surfaced to the UI — Phase 8 adds the checkout CTA)

## Verification
```bash
# Backend (JAVA_HOME must be temurin-21 — JDK 26 breaks Lombok)
cd backend
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
mvn test                      # expect prior 77 + 17 new ≈ 94 tests
mvn spring-boot:run           # start new instance (see Migration note below)

# Live curl flow against Docker PG (V4 applied on restart):
# 1. alice (TUTOR): register/login → PUT /tutors/profile {"hourlyRate": 40} → create one-off slot in +30h
# 2. bob (STUDENT): register/login → POST /bookings {"slotId": "<slot>"} → 201 CONFIRMED, amount 40.00
# 3. GET /bookings → list shows bob's booking; bob books same slot again → 409
# 4. POST /bookings/{id}/cancel → 200, refundAmount 40.00, fee 0
# 5. GET /tutors/<alice>/availability?from=&to= → slot visible again as AVAILABLE
# 6. Repeat 2–4 with a slot in +12h → cancel shows fee 20.00 / refund 20.00

# Frontend
cd frontend && npx tsc -b && npm run build
```

## Migration note (requires explicit permission)
Creating `V4__booking_tables.sql` is part of implementation, but **applying it** happens automatically when the backend restarts (Flyway runs at startup). Per `rules.md`, restarting the backend applies a migration and is a forbidden-category action — I will ask for approval to restart before the green-phase live verification.

## Deferred
- Payment capture and refund execution — Phase 8 (Stripe); `amount`/`currency`/`refund_amount`/`cancellation_fee` are already stored, so no migration is needed
- Tutor-side booking reporting / analytics — Phase 13 (tutor GET on bookings deliberately not exposed in Phase 7)
- Session start & meeting lifecycle, plus the scheduled job that auto-completes bookings — Phase 11 (transition method + tests land now)
- Email/SMS notifications on booking/cancel — Phase 9 (Spring Mail)
- Booking rescheduling — no endpoint yet; the state machine makes it a cancel+rebook flow (README lists it as a later capability)
