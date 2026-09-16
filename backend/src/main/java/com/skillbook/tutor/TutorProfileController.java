package com.skillbook.tutor;

import com.skillbook.common.ApiResponse;
import com.skillbook.skill.TutorSkillService;
import com.skillbook.skill.dto.SkillNamesRequest;
import com.skillbook.skill.dto.SkillResponse;
import com.skillbook.tutor.dto.TutorProfileRequest;
import com.skillbook.tutor.dto.TutorProfileResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/tutors")
@RequiredArgsConstructor
public class TutorProfileController {

        private final TutorProfileService tutorProfileService;
        private final TutorSkillService tutorSkillService;

        @PutMapping("/profile")
        @PreAuthorize("hasRole('TUTOR')")
        public ResponseEntity<ApiResponse<TutorProfileResponse>> upsertOwnProfile(
                        Authentication authentication,
                        @Valid @RequestBody TutorProfileRequest request) {
                TutorProfileResponse response = tutorProfileService.upsertProfile(
                                authentication.getName(), request);
                return ResponseEntity.ok(ApiResponse.ok(response));
        }

        @GetMapping("/profile")
        @PreAuthorize("hasRole('TUTOR')")
        public ResponseEntity<ApiResponse<TutorProfileResponse>> getOwnProfile(
                        Authentication authentication) {
                TutorProfileResponse response = tutorProfileService
                                .getByEmail(authentication.getName());
                return ResponseEntity.ok(ApiResponse.ok(response));
        }

        @GetMapping("/{userId}")
        public ResponseEntity<ApiResponse<TutorProfileResponse>> getTutorProfile(
                        @PathVariable UUID userId) {
                TutorProfileResponse response = tutorProfileService.getByUserId(userId);
                return ResponseEntity.ok(ApiResponse.ok(response));
        }

        @PutMapping("/profile/skills")
        @PreAuthorize("hasRole('TUTOR')")
        public ResponseEntity<ApiResponse<List<SkillResponse>>> replaceOwnOfferedSkills(
                        Authentication authentication,
                        @Valid @RequestBody SkillNamesRequest request) {
                List<SkillResponse> response = tutorSkillService.replaceOfferedSkills(
                                authentication.getName(), request.skills());
                return ResponseEntity.ok(ApiResponse.ok(response));
        }

        @GetMapping("/profile/skills")
        @PreAuthorize("hasRole('TUTOR')")
        public ResponseEntity<ApiResponse<List<SkillResponse>>> getOwnOfferedSkills(
                        Authentication authentication) {
                List<SkillResponse> response = tutorSkillService
                                .listOfferedSkills(authentication.getName());
                return ResponseEntity.ok(ApiResponse.ok(response));
        }
}