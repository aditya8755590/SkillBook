package com.skillbook.availability.dto;

import jakarta.validation.constraints.NotNull;

public record OneOffSlotRequest(
        @NotNull(message = "startAt is required")
        String startAt,

        @NotNull(message = "endAt is required")
        String endAt
) {
}