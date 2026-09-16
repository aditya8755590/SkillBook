package com.skillbook.skill;

import com.skillbook.exception.ResourceNotFoundException;
import com.skillbook.skill.dto.SkillResponse;
import com.skillbook.user.User;
import com.skillbook.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TutorSkillService {

    private final UserRepository userRepository;
    private final SkillRepository skillRepository;
    private final SkillOfferedRepository skillOfferedRepository;

    @Transactional
    public List<SkillResponse> replaceOfferedSkills(String email, List<String> skillNames) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

        skillOfferedRepository.deleteByTutorId(user.getId());
        skillOfferedRepository.flush();

        List<com.skillbook.skill.Skill> skills = skillNames.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .map(name -> getOrCreateSkill(name))
                .toList();

        for (com.skillbook.skill.Skill skill : skills) {
            skillOfferedRepository.save(new SkillOffered(user, skill));
        }

        return skills.stream()
                .sorted(java.util.Comparator.comparing(com.skillbook.skill.Skill::getName))
                .map(SkillResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SkillResponse> listOfferedSkills(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

        Collection<SkillOffered> offered = skillOfferedRepository.findByTutorIdWithSkill(user.getId());
        return offered.stream()
                .map(SkillOffered::getSkill)
                .sorted(java.util.Comparator.comparing(com.skillbook.skill.Skill::getName))
                .map(SkillResponse::from)
                .toList();
    }

    private com.skillbook.skill.Skill getOrCreateSkill(String name) {
        return skillRepository.findByNameIgnoreCase(name)
                .orElseGet(() -> skillRepository.save(new com.skillbook.skill.Skill(name, null)));
    }
}