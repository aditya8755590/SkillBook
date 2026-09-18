package com.skillbook.payment;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StripeGatewayImplTest {

    @Test
    void rejectsBogusWebhookSignature() {
        StripeGatewayImpl gateway =
                new StripeGatewayImpl("sk_test_dummy", "whsec_dummy");

        assertThatThrownBy(() -> gateway.constructEvent("{}", "t=0,v1=not-a-real-signature"))
                .isInstanceOf(PaymentSignatureException.class)
                .hasMessage("Invalid Stripe webhook signature");
    }
}