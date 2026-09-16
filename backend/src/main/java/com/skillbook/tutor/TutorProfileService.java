package com.skillbook.tutor;

import com.skillbook.exception.ResourceNotFoundException;
import com.skillbook.skill.SkillOffered;
import com.skillbook.skill.SkillOfferedRepository;
import com.skillbook.skill.dto.SkillResponse;
import com.skillbook.tutor.dto.TutorProfileRequest;
import com.skillbook.tutor.dto.TutorProfileResponse;
import com.skillbook.user.User;
import com.skillbook.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TutorProfileService {

        private final UserRepository userRepository;
        private final TutorProfileRepository tutorProfileRepository;
        private final SkillOfferedRepository skillOfferedRepository;

        @Transactional
        public TutorProfileResponse upsertProfile(String email, TutorProfileRequest request) {
                User user = userRepository.findByEmail(email)
                                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

                boolean isNew = user.getTutorProfile() == null;
                TutorProfile profile = user.getTutorProfile();
                if (isNew) {
                        profile = new TutorProfile();
                        profile.setUser(user);
                }

                if (request.bio() != null) {
                        profile.setBio(request.bio());
                }
                if (request.experienceYears() != null) {
                        profile.setExperienceYears(request.experienceYears());
                }
                if (request.hourlyRate() != null) {
                        profile.setHourlyRate(request.hourlyRate());
                }

                tutorProfileRepository.saveAndFlush(profile);
                return TutorProfileResponse.from(user, profile);
        }

        @Transactional(readOnly = true)
        public TutorProfileResponse getByUserId(UUID userId) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

                TutorProfile profile = user.getTutorProfile();
                if (profile == null) {
                        throw new ResourceNotFoundException("TutorProfile", "userId", userId);
                }

                return TutorProfileResponse.from(user, profile, loadSkills(user.getId()));
        }

        @Transactional(readOnly = true)
        public TutorProfileResponse getByEmail(String email) {
                User user = userRepository.findByEmail(email)
                                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

                TutorProfile profile = user.getTutorProfile();
                if (profile == null) {
                        throw new ResourceNotFoundException("TutorProfile", "email", email);
                }

                return TutorProfileResponse.from(user, profile, loadSkills(user.getId()));
        }

        private List<SkillResponse> loadSkills(UUID userId) {
                return skillOfferedRepository.findByTutorIdWithSkill(userId).stream()
                                .map(SkillOffered::getSkill)
                                .map(SkillResponse::from)
                                .sorted(Comparator.comparing(SkillResponse::name))
                                .toList();
        }
}