package com.skillbook.tutor.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record TutorProfileRequest(
        @Size(max = 2000) String bio,
        @Min(0) Integer experienceYears,
        @DecimalMin("0.0") @Digits(integer = 6, fraction = 2) java.math.BigDecimal hourlyRate
) {
}