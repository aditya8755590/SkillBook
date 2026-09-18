package com.skillbook.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByBookingId(UUID bookingId);

    Optional<Payment> findByStripeCheckoutSessionId(String stripeCheckoutSessionId);

    Optional<Payment> findByStripeRefundId(String stripeRefundId);
}