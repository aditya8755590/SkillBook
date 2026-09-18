package com.skillbook.payment.dto;

import com.skillbook.payment.Payment;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentInfo(
        UUID id,
        String status,
        BigDecimal amount,
        String currency,
        BigDecimal refundAmount
) {
    public static PaymentInfo from(Payment payment) {
        if (payment == null) return null;
        return new PaymentInfo(
                payment.getId(),
                payment.getStatus().name(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getRefundAmount()
        );
    }
}