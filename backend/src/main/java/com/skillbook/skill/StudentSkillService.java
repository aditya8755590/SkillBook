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
public class StudentSkillService {

    private final UserRepository userRepository;
    private final SkillRepository skillRepository;
    private final SkillWantedRepository skillWantedRepository;

    @Transactional
    public List<SkillResponse> replaceWantedSkills(String email, List<String> skillNames) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

        skillWantedRepository.deleteByStudentId(user.getId());
        skillWantedRepository.flush();

        List<Skill> skills = skillNames.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .map(name -> getOrCreateSkill(name))
                .toList();

        for (Skill skill : skills) {
            skillWantedRepository.save(new SkillWanted(user, skill));
        }

        return skills.stream()
                .sorted(java.util.Comparator.comparing(Skill::getName))
                .map(SkillResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SkillResponse> listWantedSkills(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

        Collection<SkillWanted> wanted = skillWantedRepository.findByStudentIdWithSkill(user.getId());
        return wanted.stream()
                .map(SkillWanted::getSkill)
                .sorted(java.util.Comparator.comparing(Skill::getName))
                .map(SkillResponse::from)
                .toList();
    }

    private Skill getOrCreateSkill(String name) {
        return skillRepository.findByNameIgnoreCase(name)
                .orElseGet(() -> skillRepository.save(new Skill(name, null)));
    }
}