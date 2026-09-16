package com.skillbook.entities;

import com.skillbook.user.StudentProfile;
import com.skillbook.user.User;
import com.skillbook.user.UserRepository;
import com.skillbook.common.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class UserEntityTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private com.skillbook.user.StudentProfileRepository studentProfileRepository;

    private User createUser(Role role) {
        User user = new User();
        user.setEmail("student@example.com");
        user.setPasswordHash("$2a$10$abcdefghijklmnopqrstuv");
        user.setName("Test Student");
        user.setRole(role);
        return user;
    }

    @Test
    void savesUserWithGeneratedUuidAndTimestamps() {
        User saved = userRepository.saveAndFlush(createUser(Role.STUDENT));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getEmail()).isEqualTo("student@example.com");
        assertThat(saved.getRole()).isEqualTo(Role.STUDENT);
    }

    @Test
    void findsUserByEmail() {
        userRepository.saveAndFlush(createUser(Role.TUTOR));

        Optional<User> found = userRepository.findByEmail("student@example.com");

        assertThat(found).isPresent();
        assertThat(found.get().getRole()).isEqualTo(Role.TUTOR);
    }

    @Test
    void assignsRoleToNewUser() {
        UUID id = userRepository.saveAndFlush(createUser(Role.ADMIN)).getId();

        Optional<User> found = userRepository.findById(id);

        assertThat(found).isPresent();
        assertThat(found.get().getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void cascadesStudentProfileOnUserDelete() {
        User user = createUser(Role.STUDENT);
        StudentProfile profile = new StudentProfile();
        profile.setEducationLevel("High School");
        user.addStudentProfile(profile);
        userRepository.saveAndFlush(user);

        assertThat(profile.getId()).isNotNull();
        assertThat(studentProfileRepository.findById(profile.getId())).isPresent();

        userRepository.delete(user);
        userRepository.flush();

        assertThat(studentProfileRepository.findById(profile.getId())).isEmpty();
    }
}