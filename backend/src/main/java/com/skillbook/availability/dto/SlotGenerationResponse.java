package com.skillbook.availability.dto;

public record SlotGenerationResponse(
        int generated,
        int skipped,
        String startDate,
        String endDate
) {
}