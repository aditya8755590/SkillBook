package com.skillbook.tutor.dto;

import com.skillbook.skill.dto.SkillResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record TutorSearchResponse(
    UUID id,
    UUID userId,
    String name,
    String bio,
    int experienceYears,
    BigDecimal hourlyRate,
    BigDecimal rating,
    List<SkillResponse> skills,
    LocalDateTime createdAt
) {}
