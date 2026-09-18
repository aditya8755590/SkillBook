package com.skillbook.booking.dto;

import com.skillbook.availability.AvailabilitySlot;

import java.util.UUID;

public record BookingSlotInfo(
        UUID id,
        String startAt,
        String endAt
) {
    public static BookingSlotInfo from(AvailabilitySlot slot) {
        return new BookingSlotInfo(
                slot.getId(),
                slot.getStartAt().toInstant().toString(),
                slot.getEndAt().toInstant().toString()
        );
    }
}