package com.skillbook.payment;

import com.skillbook.booking.Booking;
import com.skillbook.booking.BookingRepository;
import com.skillbook.booking.BookingStatus;
import com.skillbook.common.Role;
import com.skillbook.exception.ResourceNotFoundException;
import com.skillbook.payment.dto.CheckoutResponse;
import com.skillbook.payment.dto.PaymentInfo;
import com.skillbook.user.User;
import com.skillbook.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final String PRODUCT_NAME = "SkillBook tutoring session";

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final StripeGateway stripeGateway;

    @Value("${stripe.return-url:http://localhost:5173/bookings}")
    private String returnUrl;

    /**
     * Transactional so the PENDING row and its checkout session id commit
     * together. A transient Stripe failure rolls back the whole attempt,
     * leaving no dangling row; the student simply retries for a fresh session.
     */
    @Transactional
    public CheckoutResponse createCheckout(String email, UUID bookingId) {
        User student = findStudentByEmail(email);
        Booking booking = bookingRepository.findByIdAndStudentId(bookingId, student.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking", "id", bookingId));

        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new IllegalStateException("Only confirmed bookings can be paid");
        }
        if (booking.getAmount().signum() == 0) {
            return new CheckoutResponse(null, PaymentStatus.PAID.name());
        }

        Payment payment = paymentRepository.findByBookingId(bookingId).orElse(null);
        PaymentStatus current = payment != null ? payment.getStatus() : null;
        if (current == PaymentStatus.PAID || current == PaymentStatus.REFUNDED
                || current == PaymentStatus.PARTIALLY_REFUNDED) {
            throw new IllegalStateException("Booking is already settled");
        }
        if (payment == null) {
            payment = paymentRepository.save(new Payment(booking, student, booking.getAmount()));
        }

        StripeGateway.CheckoutSessionResult result = createSession(payment);
        payment.setStripeCheckoutSessionId(result.sessionId());
        payment.setStatus(PaymentStatus.PENDING);
        return new CheckoutResponse(result.url(), PaymentStatus.PENDING.name());
    }

    /**
     * Transactional so the whole event handling (including lazy associations
     * such as booking status) runs in one persistence context.
     */
    @Transactional
    public void handleWebhook(String payload, String signatureHeader) {
        StripeGateway.WebhookEvent event = stripeGateway.constructEvent(payload, signatureHeader);
        switch (event.type()) {
            case "checkout.session.completed" -> onCheckoutCompleted(event);
            case "checkout.session.expired" -> onCheckoutExpired(event);
            default -> log.info("Ignoring unhandled Stripe event: {}", event.type());
        }
    }

    @Transactional
    public void onCheckoutCompleted(StripeGateway.WebhookEvent event) {
        String sessionId = event.checkoutSessionId();
        if (sessionId == null) {
            log.warn("checkout.session.completed delivered without a session id; ignoring");
            return;
        }
        Payment payment = paymentRepository.findByStripeCheckoutSessionId(sessionId).orElse(null);
        if (payment == null) {
            log.warn("Webhook references unknown checkout session {}; ignoring", sessionId);
            return;
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            log.info("Payment {} already settled ({}); ignoring duplicate webhook", payment.getId(), payment.getStatus());
            return;
        }

        payment.setStatus(PaymentStatus.PAID);
        payment.setStripePaymentIntentId(event.paymentIntentId());
        payment.setPaidAt(OffsetDateTime.now(ZoneOffset.UTC));

        Booking booking = payment.getBooking();
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            log.info("Booking {} was cancelled while paying; refunding immediately", booking.getId());
            refundForCancelIfPaid(booking);
        }
    }

    @Transactional
    public void onCheckoutExpired(StripeGateway.WebhookEvent event) {
        String sessionId = event.checkoutSessionId();
        if (sessionId == null) return;
        paymentRepository.findByStripeCheckoutSessionId(sessionId)
                .filter(p -> p.getStatus() == PaymentStatus.PENDING)
                .ifPresent(p -> p.setStatus(PaymentStatus.FAILED));
    }

    @Transactional
    public void refundForCancelIfPaid(Booking booking) {
        Payment payment = paymentRepository.findByBookingId(booking.getId()).orElse(null);
        if (payment == null || payment.getStatus() != PaymentStatus.PAID) {
            return;
        }
        BigDecimal refundAmount = booking.getRefundAmount();
        if (refundAmount == null || refundAmount.signum() <= 0) {
            return;
        }
        StripeGateway.RefundResult refund = stripeGateway.refund(new StripeGateway.RefundRequest(
                payment.getStripePaymentIntentId(),
                toCents(refundAmount),
                payment.getCurrency()
        ));
        payment.setStripeRefundId(refund.id());
        payment.setRefundAmount(refundAmount);
        payment.setRefundedAt(OffsetDateTime.now(ZoneOffset.UTC));
        payment.setStatus(refundAmount.compareTo(payment.getAmount()) >= 0
                ? PaymentStatus.REFUNDED
                : PaymentStatus.PARTIALLY_REFUNDED);
    }

    public Optional<PaymentInfo> paymentInfoForBooking(UUID bookingId) {
        return paymentRepository.findByBookingId(bookingId).map(PaymentInfo::from);
    }

    private StripeGateway.CheckoutSessionResult createSession(Payment payment) {
        StripeGateway.CheckoutRequest request = new StripeGateway.CheckoutRequest(
                payment.getBooking().getId(),
                payment.getId(),
                toCents(payment.getAmount()),
                payment.getCurrency(),
                returnUrl,
                PRODUCT_NAME
        );
        return stripeGateway.createCheckoutSession(request);
    }

    private long toCents(BigDecimal amount) {
        return amount.movePointRight(2).longValueExact();
    }

    private User findStudentByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
        if (user.getRole() != Role.STUDENT) {
            throw new IllegalArgumentException("Only students can pay for bookings");
        }
        return user;
    }
}