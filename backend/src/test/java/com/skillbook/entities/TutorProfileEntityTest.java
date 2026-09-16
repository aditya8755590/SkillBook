package com.skillbook.entities;

import com.skillbook.common.Role;
import com.skillbook.tutor.TutorProfile;
import com.skillbook.user.User;
import com.skillbook.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class TutorProfileEntityTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private com.skillbook.tutor.TutorProfileRepository tutorProfileRepository;

    @Test
    void persistsOneToOneTutorProfile() {
        User user = new User();
        user.setEmail("tutor@example.com");
        user.setPasswordHash("$2a$10$abcdefghijklmnopqrstuv");
        user.setName("Test Tutor");
        user.setRole(Role.TUTOR);

        TutorProfile profile = new TutorProfile();
        profile.setBio("Experienced math tutor");
        profile.setExperienceYears(5);
        profile.setHourlyRate(new BigDecimal("45.00"));

        user.addTutorProfile(profile);
        userRepository.saveAndFlush(user);

        assertThat(profile.getId()).isNotNull();
        Optional<TutorProfile> found = tutorProfileRepository.findById(profile.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getBio()).isEqualTo("Experienced math tutor");
        assertThat(found.get().getExperienceYears()).isEqualTo(5);
        assertThat(found.get().getHourlyRate()).isEqualByComparingTo(new BigDecimal("45.00"));
        assertThat(found.get().getUser().getId()).isEqualTo(user.getId());
    }

    @Test
    void defaultsRatingToZero() {
        User user = new User();
        user.setEmail("tutor2@example.com");
        user.setPasswordHash("$2a$10$abcdefghijklmnopqrstuv");
        user.setName("Fresh Tutor");
        user.setRole(Role.TUTOR);

        TutorProfile profile = new TutorProfile();
        user.addTutorProfile(profile);
        userRepository.saveAndFlush(user);

        Optional<TutorProfile> found = tutorProfileRepository.findById(profile.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getRating()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}