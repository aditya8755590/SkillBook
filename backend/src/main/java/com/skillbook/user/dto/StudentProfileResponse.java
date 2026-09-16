package com.skillbook.user.dto;

import com.skillbook.user.StudentProfile;
import com.skillbook.user.User;

import java.time.LocalDateTime;
import java.util.UUID;

public record StudentProfileResponse(
        UUID id,
        UUID userId,
        String name,
        String email,
        String bio,
        String educationLevel,
        LocalDateTime createdAt
) {
        public static StudentProfileResponse from(User user, StudentProfile profile) {
                return new StudentProfileResponse(
                        profile.getId(),
                        user.getId(),
                        user.getName(),
                        user.getEmail(),
                        profile.getBio(),
                        profile.getEducationLevel(),
                        profile.getCreatedAt()
                );
        }
}