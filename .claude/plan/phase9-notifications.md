# Implementation Plan: Phase 9 — Notifications + Session Lifecycle

## Task Type
- [ ] In-app notifications: `notifications` table (user, type, message, read flag, booking link) with list / unread-count / mark-read / mark-all-read endpoints
- [ ] Event fan-out: booking create, booking cancel, payment received, refund issued, session completed, session reminder — each writes in-app rows for the affected parties (student **and** tutor)
- [ ] Email via Spring Mail: booking confirmation, cancellation, payment receipt, session reminder — disabled by default (`mail.enabled=false`), failures logged and swallowed (never break a business transaction)
- [ ] Session lifecycle jobs: `@EnableScheduling` + a scheduler component that (a) completes ended CONFIRMED bookings every minute and (b) sends the one-time 24h session reminder; jobs are property-gated so tests invoke them deterministically
- [ ] Reminder idempotency: new `bookings.reminder_sent_at` column; the reminder runs exactly once per booking
- [ ] Frontend (minimal): bell in `AppShell` header with unread badge + dropdown panel, poll for unread count, mark read on click

## Routes

| Method | Path | Role | Purpose |
|--------|------|------|---------|
| GET | `/api/notifications` | any authenticated | Latest 50 notifications for the caller, newest first → **200** `[{ id, type, message, read, bookingId, createdAt }]` |
| GET | `/api/notifications/unread-count` | any authenticated | → **200** `{ count }` |
| POST | `/api/notifications/{id}/read` | OWNER | Mark one read → **200**; **404** unknown or not owned |
| POST | `/api/notifications/read-all` | any authenticated | Mark all caller's read → **200** `{ count }` (number marked) |

No new `permitAll` entries — `anyRequest().authenticated()` already covers these; missing/expired JWT → **401** via the existing entry point.

## New Flyway migration: `V6__notification_tables.sql`

`notifications`
- `id UUID PK DEFAULT gen_random_uuid()`
- `user_id UUID NOT NULL FK → users(id) ON DELETE CASCADE`
- `booking_id UUID NULL FK → bookings(id) ON DELETE SET NULL` (link survives booking delete only as history; user delete takes their notifications with them)
- `type VARCHAR(30) NOT NULL` — `BOOKING_CONFIRMED | BOOKING_CANCELLED | NEW_BOOKING | PAYMENT_RECEIPT | PAYMENT_REFUNDED | SESSION_REMINDER | SESSION_COMPLETED`
- `message VARCHAR(500) NOT NULL`
- `is_read BOOLEAN NOT NULL DEFAULT FALSE`
- `created_at`, `updated_at` (BaseEntity convention)
- `INDEX idx_notifications_user (user_id, is_read, created_at)`

`bookings` (alter)
- `ADD COLUMN reminder_sent_at TIMESTAMPTZ NULL` — one-shot reminder marker

Tests use H2 + `ddl-auto: create-drop` (Flyway off), so the `Notification` entity + `reminderSentAt` field must produce equivalent DDL; `@OnDelete(CASCADE/SET NULL)` annotations keep the H2 FK behaviour identical so existing tests' `@BeforeEach` cleanup (which deletes users) does not need a `notificationRepository.deleteAll()` line in every class. If H2 still raises FK violations in the suite, fall back to adding the one-line clear (Phase 7/8 pattern) to the affected classes.

## Decisions

### 1. Dependency direction: events flow *into* `notification`, never out
`NotificationService` depends only on `NotificationRepository`, `UserRepository`, and `MailService` (plus the `Booking` **entity** for association reads). `BookingService` and `PaymentService` call `notificationService.notifyX(...)`; the scheduler is a thin separate bean (`booking/SessionScheduler`) that calls `BookingService` methods. No constructor-injection cycle (Spring Boot 3 disallows them).

Convenience methods keep call sites to one line per event:
- `notifyBookingCreated(booking)` → student `BOOKING_CONFIRMED`, tutor `NEW_BOOKING` + confirmation email to student
- `notifyBookingCancelled(booking)` → both `BOOKING_CANCELLED` + email to student
- `notifyPaymentReceived(booking, amount)` → student `PAYMENT_RECEIPT` + receipt email (Phase 8 deferred item)
- `notifyPaymentRefunded(booking, amount)` → student `PAYMENT_REFUNDED` + email
- `notifySessionCompleted(booking)` → both `SESSION_COMPLETED` (in-app only)
- `notifySessionReminder(booking)` → both `SESSION_REMINDER` + email to both

Message text is built from slot times (UTC, `MMM d, HH:mm`) and counterparty name, ≤500 chars.

### 2. Mail is an optional, non-blocking channel
Add `spring-boot-starter-mail`; `spring.mail.*` reads `MAIL_HOST/PORT/USERNAME/PASSWORD` (already reserved in `.env.example` + README table). New property `mail.enabled=${MAIL_ENABLED:false}` gates sending — `MailService.send(...)` no-ops when disabled and wraps every real send in try/catch (SMTP outage logs a warning; the booking/rollback risk never materialises). Tests: `application-test.yml` keeps it off for the integration suite; `MailServiceTest` (pure Mockito, mocked `JavaMailSender`) proves enabled→send, failure→swallowed, disabled→never touched. Synchronous on purpose — async would need `@EnableAsync` + executor config for no portfolio gain; SMTP latency only affects the request that triggered it.

### 3. Two scheduled jobs, one scheduler bean, property-gated
`booking/SessionScheduler` with `@Component` + `@ConditionalOnProperty(name = "app.scheduler.enabled", havingValue = "true", matchIfMissing = true)`:
- `@Scheduled(fixedDelayString = "PT1M")` → `bookingService.completeExpiredBookings()` (now also fires `notifySessionCompleted` for each flip)
- `@Scheduled(fixedDelayString = "PT5M")` → `bookingService.sendSessionReminders()`

`SkillBookApplication` gains `@EnableScheduling`. `application-test.yml` sets `app.scheduler.enabled: false` so background jobs never race the suite; tests call `bookingService.completeExpiredBookings()` / `sendSessionReminders()` directly (existing convention — `completedTransitionWhenSessionPasses` already does this).

### 4. Reminder contract
`sendSessionReminders()`: load `CONFIRMED` bookings with `slot.startAt` in `(now, now+24h]` and `reminder_sent_at IS NULL` → for each: set `reminderSentAt`, persist, `notifySessionReminder(booking)`. Idempotent by the column, not by scanning existing notifications. Cancelled/started/completed sessions are excluded by the status + window predicates. New repository query `findByStatusAndSlot_StartAtBetweenAndReminderSentAtIsNull`.

### 5. Where each event fires (integration points)
- `BookingService.createBooking` — after save: `notifyBookingCreated`
- `BookingService.cancelBooking` — after refund side-effect: `notifyBookingCancelled` (student sees fee/refund policy reflected in the message only as "cancelled"; money detail lives in the refund notification)
- `PaymentService.onCheckoutCompleted` — only when the flip to PAID actually happened **and** the booking is still CONFIRMED: `notifyPaymentReceived`. (Cancelled-while-paying path already refunds → that path fires `notifyPaymentRefunded` from `refundForCancelIfPaid` when a refund executes.)
- `PaymentService.refundForCancelIfPaid` — after a successful gateway refund: `notifyPaymentRefunded`
- `BookingService.completeExpiredBookings` — per flipped booking: `notifySessionCompleted`
- `BookingService.sendSessionReminders` — per due booking: `notifySessionReminder`

### 6. Response shape
`NotificationResponse` DTO: `id, type, message, read, bookingId, createdAt` (ISO-8601 via Jackson config). No Stripe or internal identifiers. `unread-count` returns `{ count }` to fit the `ApiResponse` envelope.

## Domain files created (package `com.skillbook.notification`)
- `Notification.java` (entity — `@Table(name="notifications")`, `@ManyToOne` user/booking, `@OnDelete` FK actions, boolean `read` mapped to `is_read`)
- `NotificationType.java` (7 values above)
- `NotificationRepository.java` (`findAllByUserIdOrderByCreatedAtDesc`, `countByUserIdAndReadFalse`, `findByIdAndUserId`, `deleteByUserId` not needed — cascade)
- `NotificationService.java` (queries + six `notifyX` fan-out methods; injects `NotificationRepository`, `UserRepository`, `MailService`)
- `MailService.java` (`send(to, subject, body)` + four typed wrappers; `@Value("${mail.enabled:false}")`; `ObjectProvider<JavaMailSender>`)
- `NotificationController.java` (`GET /notifications`, `GET /notifications/unread-count`, `POST /notifications/{id}/read`, `POST /notifications/read-all`)
- `dto/NotificationResponse.java`, `dto/UnreadCountResponse.java`

## Files modified
- `backend/pom.xml` (+ `spring-boot-starter-mail`)
- `backend/src/main/resources/db/migration/V6__notification_tables.sql`
- `backend/src/main/resources/application.yml` (+ `spring.mail.*`, `mail.enabled`), `application-test.yml` (+ `app.scheduler.enabled: false`)
- `SkillBookApplication.java` (+ `@EnableScheduling`)
- `booking/BookingService.java` (event fan-out + `sendSessionReminders()`), `booking/Booking.java` (+ `reminderSentAt`), `booking/BookingRepository.java` (+ reminder query), new `booking/SessionScheduler.java`
- `payment/PaymentService.java` (receipt + refund notifications)
- Existing integration tests: no change expected (H2 `@OnDelete` cascade); only if FK violations appear
- Frontend: `types/index.ts` (+ `AppNotification`), new `services/notifications.ts`, new `components/NotificationBell.tsx`, `components/AppShell.tsx` (mount bell)
- `README.md` (Phase 9 → ✅, test-suite line refresh)

## Test cases (RED first)

`NotificationIntegrationTest` (MockMvc + `@SpringBootTest` + H2, fixtures reuse Phase 7/8 style: alice TUTOR rate 40, bob STUDENT, optional carol; `@MockBean StripeGateway` for the paid paths; cleanup order notification → payment → booking → slot → rule → skill → user):

1. `bookingConfirmationNotifiesStudentAndTutor` — book → student's `GET /notifications` has `BOOKING_CONFIRMED` unread; tutor has `NEW_BOOKING`
2. `bookingConfirmationNotifiesBothPartiesViaCount` — both users' unread-count ≥ 1 after one booking
3. `cancelNotifiesBothParties` — cancel → `BOOKING_CANCELLED` rows for student and tutor
4. `paymentReceiptNotifiedOnWebhook` — checkout + completed webhook → student `PAYMENT_RECEIPT` with amount in message
5. `refundNotifiedWhenGatewayRefunds` — paid booking cancelled → student `PAYMENT_REFUNDED`
6. `sessionCompletionNotifiesBothParties` — plant past CONFIRMED booking → `completeExpiredBookings()` → status COMPLETED + `SESSION_COMPLETED` for student and tutor
7. `reminderSentOnceWithin24hWindow` — booking starting in 2h → `sendSessionReminders()` → `SESSION_REMINDER` for both + `reminderSentAt` set; second run adds nothing (counts unchanged)
8. `reminderSkipsCancelledAndFarFutureBookings` — cancelled booking + booking starting in 3 days → no reminder rows
9. `listReturnsOwnNotificationsNewestFirst` — seeded rows → only caller's, ordered desc by createdAt
10. `unreadCountReflectsReadState` — count > 0 → mark read → count decreases; read-all → 0
11. `markOwnNotificationRead` — POST `/{id}/read` → 200, `read=true` on next GET
12. `markReadOthersNotificationReturns404` — carol marks bob's notification → 404
13. `notificationsRequireAuthentication` — GET `/notifications` without token → 401

`MailServiceTest` (pure Mockito, no Spring):
14. `sendsMailWhenEnabled` — enabled + mock sender → `send(MimeMessage)` called once with the configured recipient
15. `sendFailureIsSwallowed` — mock throws `MailException` → no propagation
16. `disabledNeverTouchesSender` — `mail.enabled=false` → `verifyNoInteractions`

Existing `BookingIntegrationTest.completedTransitionWhenSessionPasses` must stay green (it now also creates notifications — harmless).

## Verification
```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
cd backend && mvn test        # 112 prior + ~16 new ≈ 128
cd frontend && npx tsc -b && npm run build
```

## Migration note (requires explicit permission)
`V6__notification_tables.sql` applies on the next backend restart (Flyway) against the Docker/native PostgreSQL — forbidden category per `rules.md`, approval will be requested before any live run. `mvn test` (approved) uses in-memory H2 only. Live email verification is skipped unless `MAIL_*` credentials are set; otherwise `MailServiceTest` + the disabled-by-default gate are the evidence.

## Deferred
- Real-time push (SSE/WebSocket notification stream) — Phase 10 WebSocket may carry it; polling bell ships now
- Per-user notification preferences / unsubscribe — out of scope
- Tutor-side booking management endpoints (tutor cancel) — still open from Phase 7
- Reviews prompt after `SESSION_COMPLETED` — Phase 12
- Async refund retry / dead-letter — Phase 13
