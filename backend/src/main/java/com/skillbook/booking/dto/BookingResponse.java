package com.skillbook.booking.dto;

import com.skillbook.booking.Booking;
import com.skillbook.payment.dto.PaymentInfo;

import java.math.BigDecimal;
import java.util.UUID;

public record BookingResponse(
        UUID id,
        String status,
        String message,
        BigDecimal amount,
        String currency,
        BigDecimal refundAmount,
        BigDecimal cancellationFee,
        String cancelledAt,
        String completedAt,
        String createdAt,
        BookingSlotInfo slot,
        BookingStudentInfo student,
        BookingTutorInfo tutor,
        PaymentInfo payment
) {
    public static BookingResponse from(Booking booking) {
        return from(booking, null);
    }

    public static BookingResponse from(Booking booking, PaymentInfo payment) {
        return new BookingResponse(
                booking.getId(),
                booking.getStatus().name(),
                booking.getMessage(),
                booking.getAmount(),
                booking.getCurrency(),
                booking.getRefundAmount(),
                booking.getCancellationFee(),
                booking.getCancelledAt() != null ? booking.getCancelledAt().toInstant().toString() : null,
                booking.getCompletedAt() != null ? booking.getCompletedAt().toInstant().toString() : null,
                booking.getCreatedAt() != null ? booking.getCreatedAt().toString() : null,
                BookingSlotInfo.from(booking.getSlot()),
                BookingStudentInfo.from(booking.getStudent()),
                BookingTutorInfo.from(booking.getSlot().getTutor()),
                payment
        );
    }
}