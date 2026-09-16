package com.skillbook.skill;

import com.skillbook.skill.dto.SkillResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SkillService {

    private final SkillRepository skillRepository;

    public record CreationResult(Skill skill, boolean created) {}

    @Transactional
    public CreationResult getOrCreate(String name, String category) {
        String normalized = name.trim();
        String categoryNormalized = (category == null || category.isBlank()) ? null : category.trim();

        return skillRepository.findByNameIgnoreCase(normalized)
                .map(skill -> new CreationResult(skill, false))
                .orElseGet(() -> {
                    Skill skill = new Skill(normalized, categoryNormalized);
                    return new CreationResult(skillRepository.save(skill), true);
                });
    }

    @Transactional(readOnly = true)
    public List<SkillResponse> list(String query) {
        List<Skill> skills = (query == null || query.isBlank())
                ? skillRepository.findAllByOrderByNameAsc()
                : skillRepository.findByNameContainingIgnoreCaseOrderByNameAsc(query.trim());

        return skills.stream().map(SkillResponse::from).toList();
    }
}