package com.skillbook.availability.dto;

import com.skillbook.availability.AvailabilityRule;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

public record AvailabilityRuleResponse(
        UUID id,
        int dayOfWeek,
        String startTime,
        String endTime,
        String timezone,
        boolean active
) {
    public static AvailabilityRuleResponse from(AvailabilityRule rule) {
        return new AvailabilityRuleResponse(
                rule.getId(),
                rule.getDayOfWeek().getValue(),
                rule.getStartTime().toString(),
                rule.getEndTime().toString(),
                rule.getTimezone(),
                rule.isActive()
        );
    }
}