package com.skillbook.user;

import com.skillbook.exception.ResourceNotFoundException;
import com.skillbook.user.dto.StudentProfileRequest;
import com.skillbook.user.dto.StudentProfileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StudentProfileService {

        private final UserRepository userRepository;
        private final StudentProfileRepository studentProfileRepository;

        @Transactional
        public StudentProfileResponse upsertProfile(String email, StudentProfileRequest request) {
                User user = userRepository.findByEmail(email)
                                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

                boolean isNew = user.getStudentProfile() == null;
                StudentProfile profile = user.getStudentProfile();
                if (isNew) {
                        profile = new StudentProfile();
                        profile.setUser(user);
                }

                if (request.bio() != null) {
                        profile.setBio(request.bio());
                }
                if (request.educationLevel() != null) {
                        profile.setEducationLevel(request.educationLevel());
                }

                studentProfileRepository.saveAndFlush(profile);
                return StudentProfileResponse.from(user, profile);
        }

        @Transactional(readOnly = true)
        public StudentProfileResponse getByEmail(String email) {
                User user = userRepository.findByEmail(email)
                                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

                StudentProfile profile = user.getStudentProfile();
                if (profile == null) {
                        return null;
                }

                return StudentProfileResponse.from(user, profile);
        }
}