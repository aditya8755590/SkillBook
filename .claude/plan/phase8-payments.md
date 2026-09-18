# Implementation Plan: Phase 8 — Stripe Payments

## Task Type
- [x] Hosted Stripe Checkout: student pays a CONFIRMED booking via a generated Checkout Session URL
- [x] Webhook verification: `/payment/webhook` verifies the Stripe signature, never trusts the frontend; `checkout.session.completed` marks the payment PAID
- [x] Payment state machine: `PENDING → PAID`, `PAID → REFUNDED | PARTIALLY_REFUNDED`; `PENDING → FAILED` on session expiry; `PAID/REFUNDED` terminal for checkout
- [x] Refunds: cancel of a PAID booking executes the Phase 7 stored `refund_amount`/`cancellation_fee` exactly (full or partial), via the Stripe Refunds API
- [x] Free sessions (`amount == 0`): booked and auto-paid without Stripe (no Checkout URL, no Payment row)
- [x] One payment per booking (`UNIQUE (booking_id)`); re-checkout after a FAILED session reuses the row with a fresh session id
- [x] Frontend (minimal): "Pay" CTA on unpaid CONFIRMED bookings → redirect to Checkout; payment status surfaced on `/bookings`

## Routes

| Method | Path | Role | Purpose |
|--------|------|------|---------|
| POST | `/api/bookings/{id}/checkout` | OWNER (STUDENT) | Create/resume checkout for a CONFIRMED, unpaid booking → **200** `{ checkoutUrl, status }` (or `{ status: "PAID" }` for free sessions) |
| POST | `/api/payment/webhook` | public (signature-verified) | Stripe delivers `checkout.session.completed` / `checkout.session.expired` → mark PAID / FAILED; **200** always for verifiable-but-ignorable events |

`checkout` reuses the Phase 7 status map: **404** unknown / non-owner booking · **409** booking not CONFIRMED or payment already PAID/REFUNDED. Webhook: **401** invalid signature, **400** malformed payload, **200** otherwise.

## New Flyway migration: `V5__payment_tables.sql`

`payments`
- `id UUID PK DEFAULT gen_random_uuid()`
- `booking_id UUID NOT NULL FK → bookings(id) ON DELETE CASCADE`
- `student_id UUID NOT NULL FK → users(id) ON DELETE CASCADE` (denormalized for analytics; Phase 13 reads payments by student without a join through bookings)
- `status VARCHAR(20) NOT NULL DEFAULT 'PENDING'` — `PENDING | PAID | FAILED | REFUNDED | PARTIALLY_REFUNDED`
- `amount NUMERIC(8,2) NOT NULL` (copy of booking.amount at checkout time), `currency VARCHAR(3) NOT NULL DEFAULT 'USD' CHECK (currency='USD')`
- `stripe_checkout_session_id VARCHAR(255) NULL`, `stripe_payment_intent_id VARCHAR(255) NULL`
- `stripe_refund_id VARCHAR(255) NULL`, `refund_amount NUMERIC(8,2) NULL` (what Stripe actually returned)
- `paid_at TIMESTAMPTZ NULL`, `refunded_at TIMESTAMPTZ NULL`
- `created_at`, `updated_at` (BaseEntity convention)
- `UNIQUE (booking_id)` — one payment per booking, the double-payment backstop; `UNIQUE (stripe_checkout_session_id)` — webhook idempotency key
- `CHECK (amount >= 0)`
- `INDEX idx_payments_student (student_id)`, `INDEX idx_payments_status (status)`

## Decisions

### 1. Hosted Checkout Session, not embedded PaymentIntent
`mode=payment` Checkout Session is the canonical, secure path: Stripe hosts PCI-compliant UI; the API returns a `url` we redirect the student to; success is learned only from the webhook. We store `stripe_checkout_session_id` and never take the frontend's word. Success URL = `STRIPE_RETURN_URL` (defaults to `http://localhost:5173/bookings`); the returning user lands on `/bookings` and the webhook (usually already fired) has flipped the booking to paid.

### 2. Stripe calls sit behind a thin gateway, so tests never need real keys
`StripeGateway` interface: `createCheckoutSession(CheckoutRequest) → CheckoutSessionResult(url, sessionId)`, `refund(RefundRequest) → RefundResult(id)`, and `constructEvent(payload, sigHeader) → WebhookEvent(type, metadata)` where `WebhookEvent` is a small record carrying `checkout.session.completed | checkout.session.expired` + `{ bookingId, paymentId, checkoutSessionId }`. Production impl `StripeGatewayImpl` wraps the Stripe Java SDK (`Stripe.checkout.sessions.create`, `Stripe.refunds.create`, `Stripe.webhooks.constructEvent`). Integration tests `@MockBean` the gateway; the SDK-signature-verification boundary is unit-tested with a bogus signature → expect `SignatureVerificationException` → mapped to **401**.

### 3. Payment lifecycle is driven by the webhook — the only "paid" authority
- `checkout.session.completed`: look up Payment by `stripe_checkout_session_id` (metadata as fallback) → if `PAID/REFUNDED` already, return 200 (idempotent replay); if `FAILED`, reopen to PAID (re-covered checkoutable? — no, only PENDING→PAID and FAILED→PAID on a genuine completion is safe because a checkout can be paid after we timed it out); else set `status=PAID, pid, paid_at`. If the booking is no longer `CONFIRMED` (cancelled while the student was on the Checkout page), immediately refund the full stored `amount` and mark the payment `REFUNDED` — the platform must not keep money for a cancelled session.
- `checkout.session.expired`: PENDING → FAILED (student closed the page). Ignore if already PAID (a completion may race an expiry).
- Unknown/other event types: verify signature, return 200 (Stripe halts retries on 2xx).

### 4. `POST /bookings/{id}/checkout` contract
- Free session (`booking.amount == 0`): no Payment row, no Stripe call; return `{ status: "PAID" }` — a free session is confirmed and settled.
- Paid session, no Payment row yet: insert `PENDING` Payment + create Checkout Session (metadata `bookingId`, `paymentId`; line item = `amount`, `currency`; `mode=payment`) → return `{ checkoutUrl, status: "PENDING" }`.
- Payment exists and `FAILED` (expired session): reopen to `PENDING`, create a fresh session, update `stripe_checkout_session_id`, return new URL.
- Payment exists and `PAID / REFUNDED / PARTIALLY_REFUNDED`: → **409** (already settled).
- Booking not `CONFIRMED`: → **409**. Non-owner / unknown booking: → **404** (Phase 7 privacy convention).
- `@Transactional` only around the DB part; the Stripe network call happens after the Payment row is persisted so a transient gateway failure leaves a PENDING row a retry can reuse (no orphan money path).

### 5. Refund on cancel uses the Phase 7 stored amounts, executed once
`BookingService.cancelBooking` already stores `cancellation_fee` + `refund_amount`. Phase 8 adds: after the booking flips to `CANCELLED`, if a `PAID` payment exists and `refund_amount > 0`, call `StripeGateway.refund` once (`amount = refund_amount`; partial when a fee applies) → set `stripe_refund_id`, `refund_amount`, `refunded_at`, and `status = REFUNDED | PARTIALLY_REFUNDED`. If no PAID payment (free / unpaid cancel), no Stripe call. A refund failure (gateway throws) rolls the booking cancel back too — cancel and refund are one logical operation; the inevitable retry story lands with Phase 9/13 jobs. `stripe_refund_id` stored per booking guarantees "refunded once".

### 6. `GET /bookings` stays the single list endpoint — payment is nested, not a new route
`BookingResponse` gains an optional nested `payment: { id, status, amount, currency, refundAmount } | null`. Frontend renders payment status from the same list it already fetches. Tuition: only `Payment.getStatus`, id, and money are exposed — no Stripe identifiers leave the backend.

### 7. Webhook endpoint bypasses JWT but only after signature verification
`/payment/webhook` is `permitAll()` in `SecurityConfig` (Stripe has no JWT), with the CSRF/stateless setup unchanged. The gateway's `constructEvent` is the gate: a forged payload fails signature verification → `SignatureVerificationException` → **401**. The handler never trusts body fields directly; it reads only the derived `WebhookEvent`.

### 8. SDK dependency
Add `com.stripe:stripe-java` to `backend/pom.xml`. Java 21 + Spring Boot 3.3 compatible. Only the gateway impl imports the SDK — the domain/controllers/tests stay SDK-free.

## Domain files created (package `com.skillbook.payment`)
- `Payment.java` (entity — `@Table(name="payments")`, `@ManyToOne` booking/student, `@Enumerated(STRING)` status, money/UUID-string fields)
- `PaymentStatus.java` (`PENDING`, `PAID`, `FAILED`, `REFUNDED`, `PARTIALLY_REFUNDED`)
- `PaymentRepository.java` (`findByBookingId`, `findByStripeCheckoutSessionId`, `findByStripeRefundId`)
- `StripeGateway.java` (interface + small records `CheckoutSessionResult`, `RefundResult`, `WebhookEvent`)
- `StripeGatewayImpl.java` (production SDK wrapper — `@Component`, resolves keys from `@Value("${stripe.secret-key}")` / `stripe.webhook-secret`)
- `PaymentService.java` — `createCheckout(bookingId, email)`, `handleWebhook(payload, sigHeader)`, `refundForCancelIfPaid(booking)` (called by `BookingService`)
- `PaymentController.java` — `POST /bookings/{id}/checkout` (STUDENT), `POST /payment/webhook` (permitAll)
- `dto/CheckoutResponse.java` (checkoutUrl, status), `dto/PaymentInfo.java` (id, status, amount, currency, refundAmount — nested into BookingResponse)

Modified:
- `BookingService.java` (`cancelBooking` → after flip, `paymentService.refundForCancelIfPaid(booking)`; wire via `@Lazy` or setter to avoid a circular dependency with `PaymentService`'s booking repo reads)
- `booking/dto/BookingResponse.java` (+ `PaymentInfo payment` field and accessor through a `paymentRepository.findByBookingId`)
- `config/SecurityConfig.java` (`/payment/webhook` → permitAll)
- `config/application.yml` (+ `stripe:` secret-key/webhook-secret/return-url with env-var defaults), `.env.example` already lists the keys
- `backend/pom.xml` (+ `com.stripe:stripe-java`)

## Test cases (RED first)
`PaymentIntegrationTest` (MockMvc + `@SpringBootTest` + H2, shared context, `@MockBean StripeGateway`). Fixtures reuse the Phase 7 approach (alice TUTOR rate 40, bob STUDENT; create slot → book). `@BeforeEach` clears in FK order: payment → booking → slot → rule → skill → user. Helpers: `book(tutorToken, slotToken) → {bookingId, bookingToken}`, `fakeCheckout` (gateway returns a canned URL/sessionId via Mockito `when`).

1. `checkoutReturnsRedirectUrlForPaidBooking` — bob books a 1h slot (40.00) → `POST /bookings/{id}/checkout` → 200, `checkoutUrl` present, `status=PENDING`; Payment row exists with `status=PENDING` and stored amount 40.00
2. `checkoutForFreeSessionReturnsPaidWithoutStripe` — alice sets hourlyRate 0 → bob books → `checkout` → 200 `{ status: "PAID" }`; `StripeGateway` never invoked (`verifyNoInteractions`)
3. `checkoutTwiceReusesPendingPayment` — second checkout for the same PENDING booking → 200, same Payment id (recreated session only after FAILED), no FK/unique violation
4. `checkoutForAlreadyPaidBookingRejected` — drive Payment to PAID via webhook, then checkout → **409**
5. `checkoutNonOwnerReturns404` — carol checkout bob's booking → **404**
6. `checkoutCancelledBookingRejected` — cancel booking then checkout → **409**
7. `webhookCompletedMarksPaymentPaid` — fake gateway `constructEvent` → `checkout.session.completed` with the payment's session id → 200; Payment status `PAID`, `paid_at` set
8. `webhookCompletedIsIdempotent` — deliver the same completed event twice → second returns 200 and leaves a single PAID (no double state change)
9. `webhookCompletedForCancelledBookingAutoRefunds` — booking cancelled BEFORE webhook lands → completion marks PAID then immediately REFUNDED, refund amount = full stored amount, gateway refund called once
10. `webhookExpiredMarksFailed` — `checkout.session.expired` → Payment `FAILED`; re-checkout then succeeds (PENDING again, new session)
11. `webhookInvalidSignatureRejected` — gateway `constructEvent` throws `SignatureVerificationException` → **401**
12. `webhookUnhandledEventReturns200` — `payment_intent.succeeded` (unrelated) → verify-then-200, payment unchanged
13. `cancelPaidBookingRefundsAndMarksRefunded` — book → checkout → webhook-complete (PAID) → cancel → 200; gateway.refund called with amount 40.00 (full, ≥24h); Payment `REFUNDED`, `stripe_refund_id` set
14. `cancelPaidBookingWithin24hPartialRefund` — slot in +12h → cancel → gateway.refund called with 20.00 (fee 20 / refund 20); Payment `PARTIALLY_REFUNDED`
15. `cancelUnpaidBookingSkipsRefund` — CONFIRMED unpaid booking cancelled → gateway.refund never called
16. `getBookingsIncludesPaymentInfo` — bob lists bookings → `payment.status` present for the paid one, `null` for the unpaid one

`StripeGatewayImplTest` (pure unit, no Spring): 
17. `constructEventRejectsForgedSignature` — feed garbage payload + bogus signature with a configured webhook secret → throws (the verification boundary)
18. `createSessionBuildsExpectedParams` — `Stripe.checkout.sessions.create` mocked via SDK's static `Stripe`? (SDK is final/static; instead assert the impl computes the correct `params.metadata` by capturing — if not mockable cheaply, drop this test and keep 1–16 + 17)

## Files changed
- `backend/pom.xml` (+ stripe-java)
- `backend/src/main/resources/db/migration/V5__payment_tables.sql`
- `backend/src/main/java/com/skillbook/config/application.yml`, `backend/src/main/java/com/skillbook/config/SecurityConfig.java`
- New `payment` package (entity, enum, repo, gateway, service, controller, DTOs)
- `backend/src/main/java/com/skillbook/booking/BookingService.java`, `booking/dto/BookingResponse.java`
- `backend/src/test/java/com/skillbook/booking/BookingIntegrationTest.java` (@BeforeEach payment-clear line) + 6 other shared-context classes × 1 payment-clear line (same FK-order fix as Phase 7)
- New `backend/src/test/java/com/skillbook/payment/PaymentIntegrationTest.java`, `StripeGatewayImplTest.java`
- `frontend/src/types/index.ts` (+ `PaymentInfo`, checkout payload), `frontend/src/services/bookings.ts` (+ `createCheckout`), `frontend/src/pages/MyBookingsPage.tsx` (Pay CTA, payment status badge)

## Frontend scope (minimal)
- `MyBookingsPage`: on CONFIRMED unpaid bookings show a "Pay" button → `POST /bookings/{id}/checkout` → `window.location.href = checkoutUrl` (hosted Checkout handles the rest; success URL returns to `/bookings`); when `status === "PAID"` show a "Paid" badge; cancelled rows show refund line as today. Free sessions render "Free session".
- Types: `PaymentInfo` nested on `Booking`; no payment-specific page.

## Verification
```bash
# Backend
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
cd backend && mvn test      # 94 prior + ~18 new ≈ 112
# Frontend
cd frontend && npx tsc -b && npm run build
```

## Migration note (requires explicit permission)
`V5__payment_tables.sql` applies automatically on the next backend restart (Flyway). Per `rules.md`, restarting the backend is a forbidden-category action — approval will be requested before live verification. Live Stripe checkout + real webhook delivery are test-mode, key-dependent, and will be verified live only if `STRIPE_SECRET_KEY`/`STRIPE_WEBHOOK_SECRET` are present; otherwise the suite (mocked gateway) plus a manual curl of the checkout creation is the Phase 8 evidence.

## Deferred
- Webhook-driven async refund retry / dead-letter — Phase 9/13 (a failed refund rolls the cancel back today)
- Tutor payout / payout reporting — out of scope (Phase 13 analytics only)
- Payment receipts in email — Phase 9
- Rescheduling a paid booking — remains cancel+rebook (README-listed)