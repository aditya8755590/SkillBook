# Phase 9 — Notifications + Session Lifecycle: TDD Evidence Report

**Date:** 2026-10-08
**Branch:** `main` (Phase 9 uncommitted at time of writing; single squashed commit approved for end of phase)
**Source plan:** `.claude/plan/phase9-notifications.md`
**Verify commands:**
- Backend: `export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home && mvn test` (in `backend/`)
- Frontend: `npx tsc -b && npm run build` (in `frontend/`)

## User Journeys

1. As a student, I want an in-app notification when my booking is confirmed or cancelled, so that I know its status without refreshing.
2. As a tutor, I want to be notified of new bookings, cancellations, and completed sessions, so that I can prepare and follow up.
3. As a paying student, I want a payment receipt (and a refund notice), so that I have a record of every charge.
4. As either party, I want a one-time reminder 24h before a session starts, so that I don't miss it.
5. As a user, I want a bell in the header with an unread badge, so that I can triage notifications quickly.
6. As an operator, I want scheduled jobs to complete expired sessions and send reminders automatically, while remaining disabled in tests.

## Task Report

- **Notification storage + fan-out** — `V6__notification_tables.sql` adds `notifications` (FK `user_id` CASCADE, `booking_id` SET NULL) and `bookings.reminder_sent_at`. `BookingService` calls `notifyBookingCreated` / `notifyBookingCancelled` / `notifySessionCompleted`; `PaymentService` calls `notifyPaymentReceived` / `notifyPaymentRefunded`. `NotificationService` depends only on repositories + `MailService`, so no bean cycle with the services that call it. Verified with `mvn test`.
- **REST endpoints** — `NotificationController`: `GET /notifications` (newest first), `GET /notifications/unread-count`, `POST /notifications/{id}/read`, `POST /notifications/read-all`; all authenticated, cross-user access returns 404 (Phase 7 privacy convention). Verified with `mvn test`.
- **Email (disabled by default)** — `MailService` wraps `JavaMailSender` behind `mail.enabled` (default false); send failures are swallowed (logged) so a broken SMTP config never fails a booking/payment. Verified by `MailServiceTest` (4 unit tests).
- **Scheduler** — `SessionScheduler` runs `completeExpiredBookings()` every 1 min and `sendSessionReminders()` every 5 min, gated by `app.scheduler.enabled` (`true` in prod default, `false` in `application-test.yml`). Reminders are one-time: `reminder_sent_at` set inside a 24h window; cancelled/far-future bookings skipped. Verified with `mvn test`.
- **Frontend bell** — `types/index.ts` gains `AppNotification`; `services/notifications.ts` (list / unread count / mark read / mark all read); `components/NotificationBell.tsx` (badge, 60s poll, dropdown, outside-click close, mark-read, mark-all); mounted in `AppShell` header for logged-in users. Verified with `npx tsc -b && npm run build`.

## Red → Green journey

- **RED (compile-time):** tests written first (`NotificationIntegrationTest`, `MailServiceTest`); `mvn test` failed to compile — the Phase 9 classes/methods did not exist yet. This compile failure is the RED signal (the tests newly exercise the missing `notification` package, `BookingService.sendSessionReminders`, and `Booking.reminderSentAt`).
- **Implementation:** wrote the backend code (see Task Report), then ran `mvn test`: **128 tests, 1 failure** — `HealthCheckIntegrationTest.actuatorHealthReturnsUp` expected 200 but got 503. Root cause: adding `spring-boot-starter-mail` auto-registered a `mail` health indicator that probed `spring.mail.host` (empty/localhost) and drove `/actuator/health` to DOWN. All 16 new Phase 9 tests passed. Fix: `management.health.mail.enabled: ${MAIL_HEALTH_ENABLED:false}` (opt-in via env).
- **GREEN:** reran `mvn test` → **128/128 green, exit 0** (112 pre-existing + 12 `NotificationIntegrationTest` + 4 `MailServiceTest`).
- **Frontend:** `npx tsc -b && npm run build` → clean build.
- **Note on checkpoints:** the TDD skill's per-stage checkpoint commits were waived by explicit user approval ("one squashed commit per phase"); the RED→GREEN evidence is preserved here instead of in intermediate commits.

## Test Specification

| # | What is guaranteed | Test file:method | Type | Result | Evidence |
|---|--------------------|------------------|------|--------|----------|
| 1 | Booking confirmation notifies both student and tutor | `NotificationIntegrationTest.bookingConfirmationNotifiesStudentAndTutor` | integration | PASS | `mvn test` |
| 2 | Cancellation notifies both parties | `cancelNotifiesBothParties` | integration | PASS | `mvn test` |
| 3 | Stripe webhook completion sends a payment receipt | `paymentReceiptNotifiedOnWebhook` | integration | PASS | `mvn test` |
| 4 | Refund triggers a refund notification | `refundNotifiedWhenGatewayRefunds` | integration | PASS | `mvn test` |
| 5 | Expiring an active booking notifies both parties (session completed) | `sessionCompletionNotifiesBothParties` | integration | PASS | `mvn test` |
| 6 | Reminder sent exactly once inside the 24h window | `reminderSentOnceWithin24hWindow` | integration | PASS | `mvn test` |
| 7 | Cancelled and far-future bookings are skipped by reminders | `reminderSkipsCancelledAndFarFutureBookings` | integration | PASS | `mvn test` |
| 8 | List returns only own notifications, newest first | `listReturnsOwnNotificationsNewestFirst` | integration | PASS | `mvn test` |
| 9 | Unread count tracks read state | `unreadCountReflectsReadState` | integration | PASS | `mvn test` |
| 10 | Own notification can be marked read | `markOwnNotificationRead` | integration | PASS | `mvn test` |
| 11 | Reading someone else's notification → 404 | `markReadOthersNotificationReturns404` | integration | PASS | `mvn test` |
| 12 | Notification endpoints require authentication (401) | `notificationsRequireAuthentication` | integration | PASS | `mvn test` |
| 13 | Mail sent with recipient/subject/body when enabled | `MailServiceTest.sendsMailWhenEnabled`, `recipientSubjectAndBodyAreApplied` | unit | PASS | `mvn test` |
| 14 | Send failure never propagates (booking path can't break) | `MailServiceTest.sendFailureIsSwallowed` | unit | PASS | `mvn test` |
| 15 | Disabled mail never touches the sender | `MailServiceTest.disabledNeverTouchesSender` | unit | PASS | `mvn test` |
| 16 | Actuator health stays UP with mail starter present | `HealthCheckIntegrationTest.actuatorHealthReturnsUp` | integration | PASS | `mvn test` (after health-mail fix) |
| 17 | Frontend bell builds cleanly with strict TS | `NotificationBell` via `tsc -b` + `vite build` | build | PASS | `npm run build` |

## Coverage and Known Gaps

- Final suite: **128 tests, 0 failures, 0 errors** (`mvn test`, exit 0). No coverage tool (JaCoCo) is configured in this repo, so the 80% target is not machine-enforced.
- **Deferred by permission:** `V6__notification_tables.sql` + `bookings.reminder_sent_at` apply on the next backend restart (Flyway) — forbidden per `rules.md` without a fresh user approval; tests run against H2 create-drop instead. Real SMTP delivery (`MAIL_HOST` etc.) and live cron behavior are untested by design (mail disabled by default, scheduler off in tests).
- **Deliberately untested / deferred:** WebSocket push of notifications (bell polls every 60s instead — real-time push lands with Phase 10); per-user notification preferences / opt-out; email templates (plain-text `message` only); pagination of very large notification lists (`list` returns full history, capped by usage patterns so far).
- Scheduler timing drift is untested (jobs are invoked directly in tests; `@Scheduled` wiring is covered only by context load).
