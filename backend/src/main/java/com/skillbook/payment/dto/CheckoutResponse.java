package com.skillbook.payment.dto;

public record CheckoutResponse(
        String checkoutUrl,
        String status
) {
}