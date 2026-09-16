package com.skillbook.user.dto;

import jakarta.validation.constraints.Size;

public record StudentProfileRequest(
        @Size(max = 2000) String bio,
        @Size(max = 50) String educationLevel
) {
}