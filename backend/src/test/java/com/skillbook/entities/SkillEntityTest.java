package com.skillbook.entities;

import com.skillbook.common.Role;
import com.skillbook.skill.Skill;
import com.skillbook.skill.SkillOffered;
import com.skillbook.skill.SkillRepository;
import com.skillbook.skill.SkillOfferedRepository;
import com.skillbook.skill.SkillWanted;
import com.skillbook.skill.SkillWantedRepository;
import com.skillbook.tutor.TutorProfile;
import com.skillbook.user.User;
import com.skillbook.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class SkillEntityTest {

    @Autowired
    private SkillRepository skillRepository;

    @Autowired
    private SkillOfferedRepository skillOfferedRepository;

    @Autowired
    private SkillWantedRepository skillWantedRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void persistsSkillWithUniqueName() {
        Skill math = new Skill();
        math.setName("Mathematics");
        math.setCategory("STEM");

        Skill saved = skillRepository.save(math);

        assertThat(saved.getId()).isNotNull();
        Optional<Skill> found = skillRepository.findByName("Mathematics");
        assertThat(found).isPresent();
        assertThat(found.get().getCategory()).isEqualTo("STEM");
    }

    @Test
    void linksTutorToSkillThroughSkillOffered() {
        User tutor = new User();
        tutor.setEmail("tutor3@example.com");
        tutor.setPasswordHash("$2a$10$abcdefghijklmnopqrstuv");
        tutor.setName("Skill Tutor");
        tutor.setRole(Role.TUTOR);
        tutor.addTutorProfile(new TutorProfile());
        userRepository.saveAndFlush(tutor);

        Skill physics = skillRepository.saveAndFlush(new Skill("Physics", "STEM"));

        SkillOffered offered = new SkillOffered();
        offered.setTutor(tutor);
        offered.setSkill(physics);
        skillOfferedRepository.saveAndFlush(offered);

        assertThat(offered.getId()).isNotNull();
        Optional<SkillOffered> found = skillOfferedRepository.findById(offered.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getSkill().getName()).isEqualTo("Physics");

        Set<SkillOffered> tutorSkills =
                skillOfferedRepository.findByTutorId(tutor.getId());
        assertThat(tutorSkills).hasSize(1);
        assertThat(tutorSkills.iterator().next().getSkill().getName()).isEqualTo("Physics");
    }

    @Test
    void linksStudentToSkillThroughSkillWanted() {
        User student = new User();
        student.setEmail("student2@example.com");
        student.setPasswordHash("$2a$10$abcdefghijklmnopqrstuv");
        student.setName("Wannabe Student");
        student.setRole(Role.STUDENT);
        student.addStudentProfile(new com.skillbook.user.StudentProfile());
        userRepository.saveAndFlush(student);

        Skill biology = skillRepository.saveAndFlush(new Skill("Biology", "STEM"));

        SkillWanted wanted = new SkillWanted();
        wanted.setStudent(student);
        wanted.setSkill(biology);
        skillWantedRepository.saveAndFlush(wanted);

        assertThat(wanted.getId()).isNotNull();
        Set<SkillWanted> studentSkills =
                skillWantedRepository.findByStudentId(student.getId());
        assertThat(studentSkills).hasSize(1);
        assertThat(studentSkills.iterator().next().getSkill().getName()).isEqualTo("Biology");
    }
}