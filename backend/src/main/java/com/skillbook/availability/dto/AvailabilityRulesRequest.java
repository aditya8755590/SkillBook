package com.skillbook.availability.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record AvailabilityRulesRequest(
        @NotNull(message = "rules is required")
        List<@Valid AvailabilityRuleRequest> rules
) {
}