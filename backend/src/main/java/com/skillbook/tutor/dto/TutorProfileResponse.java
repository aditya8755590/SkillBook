package com.skillbook.tutor.dto;

import com.skillbook.tutor.TutorProfile;
import com.skillbook.user.User;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record TutorProfileResponse(
        UUID id,
        UUID userId,
        String name,
        String email,
        String bio,
        Integer experienceYears,
        BigDecimal hourlyRate,
        BigDecimal rating,
        LocalDateTime createdAt
) {
        public static TutorProfileResponse from(User user, TutorProfile profile) {
                return new TutorProfileResponse(
                        profile.getId(),
                        user.getId(),
                        user.getName(),
                        user.getEmail(),
                        profile.getBio(),
                        profile.getExperienceYears(),
                        profile.getHourlyRate(),
                        profile.getRating(),
                        profile.getCreatedAt()
                );
        }
}