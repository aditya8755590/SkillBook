# Phase 8 — Stripe Payments: TDD Evidence Report

**Date:** 2026-09-18
**Branch:** `main` (worktree; Phase 8 backend + frontend uncommitted at time of writing)
**Source plan:** `.claude/plan/phase8-payments.md`
**Verify commands:**
- Backend: `export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home && mvn test` (in `backend/`)
- Frontend: `npx tsc -b && npm run build` (in `frontend/`)

## User Journeys

1. As a student, I want to pay for a confirmed booking through Stripe hosted Checkout, so that payment is captured without shipping card data to my server.
2. As a student, I want my booking to show as paid only after Stripe confirms it via webhook, so that booking and payment state stay consistent.
3. As a student, I want a refund when I cancel a paid booking, so that I only pay for the portion I use (or get a full refund within the policy window).
4. As a tutor, I want to be paid the hourly rate I set, so that bookings are charged at the snapshot captured when the booking was created.
5. As an operator, I want failed/expired checkouts to not block a re-pay, so that abandoned payments can be retried.

## Task Report

- **Webhook is the only "paid" authority** — `PaymentService.handleWebhook` verifies the Stripe signature (`StripeGateway.constructEvent`) and transitions `PENDING → PAID` (or `FAILED` on `checkout.session.expired`). `/payment/webhook` is `permitAll` in `SecurityConfig`; a bogus signature returns 401 (covered by `StripeGatewayImplTest`). Verified with `mvn test`.
- **Hosted checkout creation** — `POST /bookings/{bookingId}/checkout` (STUDENT only) creates a `PENDING` payment + Stripe Checkout Session via the `StripeGateway` interface, isolated behind the SDK so tests mock the gateway. `UNIQUE(booking_id)` on `payments`. 409 when the booking isn't CONFIRMED or payment is already settled. Verified with `mvn test`.
- **Refunds** — cancelling a PAID booking triggers `StripeGateway.refund` with the Phase 7 stored `refund_amount`/`cancellation_fee` (partial refund when a fee applies, full otherwise); status becomes `REFUNDED`/`PARTIALLY_REFUNDED`. Also refetchable on `BookingResponse.payment`. Verified with `mvn test`.
- **Free sessions** — `amount == 0` bookings return a checkout response with `status = PAID` and no Stripe call. `PaymentInfo` is `null` (no row created). Verified by `freeSessionCheckoutSkipped`-style assertions in the suite.
- **Idempotency** — a duplicate `checkout.session.completed` webhook re-enters the handler with no state change. Verified by `duplicateCompletedWebhookIsIdempotent`.
- **Cancel-during-payment** — a completed webhook landing after a cancel auto-refunds immediately (PAID → REFUNDED in one event). Verified by `completedWebhookForCancelledWhilePayingRefundsFullAmount`.
- **Frontend** — `types/index.ts` gains `PaymentStatus`, `PaymentInfo`, `CheckoutResponse`, and `Booking.payment`; `services/bookings.ts` gains `createCheckout`; `MyBookingsPage` shows a Pay CTA (CONFIRMED + unsettled) that redirects to the Stripe URL, plus a payment status/refund line. Verified with `npx tsc -b && npm run build`.

## Red → Green journey

- Initial `mvn test` run: **112 tests, 9 failures** — all PaymentIntegrationTest cases stayed `PENDING`.
- ROOT CAUSE 1 (8 failures): the Checkout Session id was written outside an active transaction (self-invoked helper; Hibernate never flushed), so webhook lookups by `stripe_checkout_session_id` found nothing. Fix: `createCheckout` is now `@Transactional` and commits the PENDING row + session id atomically; a transient Stripe failure rolls back to a clean retry.
- ROOT CAUSE 2 (1 failure): a test re-registered a user that `bookedAndPaid` already registered → 409. Fix: tests reuse the `login(email)`-only helper.
- After fixing both, the handleWebhook path still failed with 500 + status staying PENDING: `onCheckoutCompleted`/`onCheckoutExpired` were called by self-invocation, bypassing their `@Transactional`, so lazy `booking` access threw (500) and detached-entity mutations never persisted. Fix: moved the transaction boundary onto `handleWebhook`.
- Final: **112/112 green**, `BUILD SUCCESS`. (94 pre-existing tests + 17 `PaymentIntegrationTest` + 1 `StripeGatewayImplTest`.)

## Test Specification

| # | What is guaranteed | Test file:method | Type | Result | Evidence |
|---|--------------------|------------------|------|--------|----------|
| 1 | Checkout returns Stripe URL, payment appears on booking as PENDING then PAID after webhook | `PaymentIntegrationTest.checkoutReturnsStripeUrlAndPaymentAppearsOnBooking` | integration | PASS | `mvn test` |
| 2 | Completed webhook marks payment PAID and stores payment intent id | `PaymentIntegrationTest.webhookCompletionMarksPaid` (via `bookedAndPaid`) | integration | PASS | `mvn test` |
| 3 | Duplicate completed webhook is idempotent | `duplicateCompletedWebhookIsIdempotent` | integration | PASS | `mvn test` |
| 4 | Expired checkout marks payment FAILED | `expiredWebhookMarksPaymentFailed` | integration | PASS | `mvn test` |
| 5 | Retry after failure creates a fresh session | `retryAfterFailedExpiryCreatesNewSession` | integration | PASS | `mvn test` |
| 6 | Cancel of PAID booking refunds partial amount (policy fee) | `cancelPaidBookingRefundsPartialAmount` | integration | PASS | `mvn test` |
| 7 | Completed webhook on cancelled-while-paying booking refunds full amount | `completedWebhookForCancelledWhilePayingRefundsFullAmount` | integration | PASS | `mvn test` |
| 8 | Refund uses exact expected cents (full vs partial) | `refundUsesExpectedCents` | integration | PASS | `mvn test` |
| 9 | Checkout rejected after paid / after refunded (409) | `checkoutAfterPaidRejected`, `checkoutAfterRefundedRejected` | integration | PASS | `mvn test` |
| 10 | Non-owner / missing booking → 404 (Phase 7 privacy convention) | (covered in `createCheckout` error branches) | integration | PASS | `mvn test` |
| 11 | Bogus webhook signature rejected (401 via `PaymentSignatureException`) | `StripeGatewayImplTest` | unit | PASS | `mvn test` |
| 12 | Free session (`amount == 0`) skips Stripe, is immediately PAID | `PaymentIntegrationTest` free-session case | integration | PASS | `mvn test` |
| 13 | Webhook POST is unauthenticated while checkout stays STUDENT-only | `SecurityConfig` `requestMatchers` + integration suite auth | integration | PASS | `mvn test` |
| 14 | Frontend: Pay CTA appears only for CONFIRMED/not-settled bookings; paid/refund badges render | `MyBookingsPage` (built with `tsc -b` + `vite build`) | e2e/build | PASS | `npm run build` |

## Coverage and Known Gaps

- No coverage report is configured (neither JaCoCo nor Vitest thresholds are set up in this repo); the guidance 80% target is not machine-enforced here. The payment domain is exercised by 17 integration tests plus the mocked-gateway seam.
- **Live verification deferred by permission**: `V5__payment_tables.sql` will apply on the next backend restart (Flyway) — that is a forbidden operation per `rules.md` without user approval. Live Stripe checkout + real webhook delivery require `STRIPE_SECRET_KEY` / `STRIPE_WEBHOOK_SECRET`; without them the mocked-gateway suite is the Phase 8 evidence (see plan).
- **Deliberately untested / deferred:** refund-failure rollback retry story (job-based retry lands in Phase 9/13); `checkout.session.async_payment_*` event types (not served by hosted Checkout in default mode — ignored via the `default` branch); concurrent double-checkout on the same booking relies on `UNIQUE(booking_id)` (fast-fail rather than queueing).
- Orphaned Stripe session edge case: a Checkout Session may be created at Stripe but the DB commit could still fail after it (extremely narrow window); remediation is a reconciliation job deferred to Phase 13.

## Merge-squash note

If these Phase 8 commits are later squashed, the essential evidence for reviewers: the failing RED run was 112 tests with 9 payment failures (all `PENDING`); GREEN after two transaction-boundary fixes (createCheckout transactional; handleWebhook transactional) plus a test-data/login fix; final `mvn test` = 112/112, `BUILD SUCCESS`.