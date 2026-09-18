package com.skillbook.payment;

import com.skillbook.booking.Booking;
import com.skillbook.common.BaseEntity;
import com.skillbook.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "payments", uniqueConstraints = {
        @UniqueConstraint(name = "uk_payments_booking", columnNames = "booking_id"),
        @UniqueConstraint(name = "uk_payments_checkout_session", columnNames = "stripe_checkout_session_id")
})
@Getter
@Setter
@NoArgsConstructor
public class Payment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "stripe_checkout_session_id", length = 255, unique = true)
    private String stripeCheckoutSessionId;

    @Column(name = "stripe_payment_intent_id", length = 255)
    private String stripePaymentIntentId;

    @Column(name = "stripe_refund_id", length = 255)
    private String stripeRefundId;

    @Column(name = "refund_amount", precision = 8, scale = 2)
    private BigDecimal refundAmount;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    @Column(name = "refunded_at")
    private OffsetDateTime refundedAt;

    public Payment(Booking booking, User student, BigDecimal amount) {
        this.booking = booking;
        this.student = student;
        this.amount = amount;
    }
}