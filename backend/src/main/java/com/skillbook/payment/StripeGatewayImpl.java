package com.skillbook.payment;

import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.Refund;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class StripeGatewayImpl implements StripeGateway {

    private final String secretKey;
    private final String webhookSecret;

    public StripeGatewayImpl(@Value("${stripe.secret-key:}") String secretKey,
                             @Value("${stripe.webhook-secret:}") String webhookSecret) {
        this.secretKey = secretKey;
        this.webhookSecret = webhookSecret;
        Stripe.apiKey = secretKey;
    }

    @Override
    public CheckoutSessionResult createCheckoutSession(CheckoutRequest request) {
        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(request.returnUrl())
                .setCancelUrl(request.returnUrl())
                .putMetadata("bookingId", request.bookingId().toString())
                .putMetadata("paymentId", request.paymentId().toString())
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency(request.currency().toLowerCase())
                                .setUnitAmount(request.unitAmountCents())
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName(request.productName())
                                        .build())
                                .build())
                        .build())
                .build();
        try {
            Session session = Session.create(params, RequestOptions.builder().setApiKey(secretKey).build());
            return new CheckoutSessionResult(session.getUrl(), session.getId());
        } catch (StripeException ex) {
            throw new PaymentGatewayException("Stripe could not create the checkout session", ex);
        }
    }

    @Override
    public RefundResult refund(RefundRequest request) {
        RefundCreateParams params = RefundCreateParams.builder()
                .setPaymentIntent(request.paymentIntentId())
                .setAmount(request.amountCents())
                .build();
        try {
            Refund refund = Refund.create(params, RequestOptions.builder().setApiKey(secretKey).build());
            return new RefundResult(refund.getId());
        } catch (StripeException ex) {
            throw new PaymentGatewayException("Stripe could not execute the refund", ex);
        }
    }

    @Override
    public WebhookEvent constructEvent(String payload, String signatureHeader) {
        try {
            Event event = Webhook.constructEvent(payload, signatureHeader, webhookSecret);
            return toWebhookEvent(event);
        } catch (SignatureVerificationException ex) {
            throw new PaymentSignatureException("Invalid Stripe webhook signature", ex);
        }
    }

    private WebhookEvent toWebhookEvent(Event event) {
        if ("checkout.session.completed".equals(event.getType())
                || "checkout.session.expired".equals(event.getType())) {
            Session session = event.getDataObjectDeserializer()
                    .getObject()
                    .filter(Session.class::isInstance)
                    .map(Session.class::cast)
                    .orElse(null);
            if (session != null) {
                return new WebhookEvent(
                        event.getType(),
                        session.getId(),
                        session.getPaymentIntent(),
                        session.getMetadata()
                );
            }
        }
        return new WebhookEvent(event.getType(), null, null, Map.of());
    }
}