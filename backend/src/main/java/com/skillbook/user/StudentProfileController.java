package com.skillbook.user;

import com.skillbook.common.ApiResponse;
import com.skillbook.skill.StudentSkillService;
import com.skillbook.skill.dto.SkillNamesRequest;
import com.skillbook.skill.dto.SkillResponse;
import com.skillbook.user.dto.StudentProfileRequest;
import com.skillbook.user.dto.StudentProfileResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/students")
@RequiredArgsConstructor
public class StudentProfileController {

        private final StudentProfileService studentProfileService;
        private final StudentSkillService studentSkillService;

        @PutMapping("/profile")
        @PreAuthorize("hasRole('STUDENT')")
        public ResponseEntity<ApiResponse<StudentProfileResponse>> upsertOwnProfile(
                        Authentication authentication,
                        @Valid @RequestBody StudentProfileRequest request) {
                StudentProfileResponse response = studentProfileService.upsertProfile(
                                authentication.getName(), request);
                return ResponseEntity.ok(ApiResponse.ok(response));
        }

        @GetMapping("/profile")
        @PreAuthorize("hasRole('STUDENT')")
        public ResponseEntity<ApiResponse<StudentProfileResponse>> getOwnProfile(
                        Authentication authentication) {
                StudentProfileResponse response = studentProfileService
                                .getByEmail(authentication.getName());
                return ResponseEntity.ok(ApiResponse.ok(response));
        }

        @PutMapping("/profile/skills")
        @PreAuthorize("hasRole('STUDENT')")
        public ResponseEntity<ApiResponse<List<SkillResponse>>> replaceOwnWantedSkills(
                        Authentication authentication,
                        @Valid @RequestBody SkillNamesRequest request) {
                List<SkillResponse> response = studentSkillService.replaceWantedSkills(
                                authentication.getName(), request.skills());
                return ResponseEntity.ok(ApiResponse.ok(response));
        }

        @GetMapping("/profile/skills")
        @PreAuthorize("hasRole('STUDENT')")
        public ResponseEntity<ApiResponse<List<SkillResponse>>> getOwnWantedSkills(
                        Authentication authentication) {
                List<SkillResponse> response = studentSkillService
                                .listWantedSkills(authentication.getName());
                return ResponseEntity.ok(ApiResponse.ok(response));
        }
}