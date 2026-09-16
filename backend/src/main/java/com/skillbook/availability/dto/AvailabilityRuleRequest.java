package com.skillbook.availability.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AvailabilityRuleRequest(
        @NotNull(message = "dayOfWeek is required")
        @Min(value = 1, message = "dayOfWeek must be between 1 (Monday) and 7 (Sunday)")
        @Max(value = 7, message = "dayOfWeek must be between 1 (Monday) and 7 (Sunday)")
        Integer dayOfWeek,

        @NotNull(message = "startTime is required")
        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "startTime must be in HH:mm format")
        String startTime,

        @NotNull(message = "endTime is required")
        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "endTime must be in HH:mm format")
        String endTime,

        @NotNull(message = "timezone is required")
        @Size(max = 64, message = "timezone must be at most 64 characters")
        String timezone
) {
}