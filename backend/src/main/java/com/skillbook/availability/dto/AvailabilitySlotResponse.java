package com.skillbook.availability.dto;

import com.skillbook.availability.AvailabilitySlot;

import java.time.Instant;
import java.util.UUID;

public record AvailabilitySlotResponse(
        UUID id,
        String startAt,
        String endAt,
        String status,
        String source
) {
    public static AvailabilitySlotResponse from(AvailabilitySlot slot) {
        return new AvailabilitySlotResponse(
                slot.getId(),
                slot.getStartAt().toInstant().toString(),
                slot.getEndAt().toInstant().toString(),
                slot.getStatus().name(),
                slot.getSource().name()
        );
    }
}