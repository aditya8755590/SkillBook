package com.skillbook.booking.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record BookingCreateRequest(
        @NotNull(message = "slotId is required")
        UUID slotId,

        @Size(max = 500, message = "message must be at most 500 characters")
        String message
) {
}