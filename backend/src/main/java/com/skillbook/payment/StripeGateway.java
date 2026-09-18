package com.skillbook.payment;

import java.util.Map;
import java.util.UUID;

/**
 * Boundary between the payment domain and the Stripe SDK. Integration tests
 * mock this interface; only {@link StripeGatewayImpl} touches Stripe classes.
 */
public interface StripeGateway {

    CheckoutSessionResult createCheckoutSession(CheckoutRequest request);

    RefundResult refund(RefundRequest request);

    /**
     * Verifies the Stripe signature and converts the raw payload into a small
     * domain record. Throws {@link com.stripe.exception.SignatureVerificationException}
     * when the signature is invalid.
     */
    WebhookEvent constructEvent(String payload, String signatureHeader);

    record CheckoutRequest(UUID bookingId, UUID paymentId, long unitAmountCents,
                           String currency, String returnUrl, String productName) {
    }

    record RefundRequest(String paymentIntentId, long amountCents, String currency) {
    }

    record CheckoutSessionResult(String url, String sessionId) {
    }

    record RefundResult(String id) {
    }

    record WebhookEvent(String type, String checkoutSessionId, String paymentIntentId,
                        Map<String, String> metadata) {
    }
}