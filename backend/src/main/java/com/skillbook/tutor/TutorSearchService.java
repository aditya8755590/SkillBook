package com.skillbook.tutor;

import com.skillbook.skill.SkillOffered;
import com.skillbook.skill.SkillOfferedRepository;
import com.skillbook.skill.dto.SkillResponse;
import com.skillbook.tutor.dto.TutorSearchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TutorSearchService {

    private final TutorProfileRepository tutorProfileRepository;
    private final SkillOfferedRepository skillOfferedRepository;

    private static final int MAX_SIZE = 100;

    @Transactional(readOnly = true)
    public Page<TutorSearchResponse> search(String q, String skill,
            BigDecimal minRate, BigDecimal maxRate, BigDecimal minRating,
            String sort, int page, int size) {

        Sort sortRequest = resolveSort(sort);
        int safeSize = Math.min(Math.max(size, 1), MAX_SIZE);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize, sortRequest);

        Page<TutorProfile> profiles = tutorProfileRepository.search(
                toLikePattern(q), blankToNull(skill), minRate, maxRate, minRating, pageable);

        Map<UUID, List<SkillResponse>> skillsByUserId = loadSkills(profiles.getContent());

        return profiles.map(profile -> toResponse(profile, skillsByUserId));
    }

    private Sort resolveSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "rating");
        }
        return switch (sort.trim()) {
            case "rating_desc" -> Sort.by(Sort.Direction.DESC, "rating");
            case "rating_asc" -> Sort.by(Sort.Direction.ASC, "rating");
            case "price_asc" -> Sort.by(Sort.Direction.ASC, "hourlyRate");
            case "price_desc" -> Sort.by(Sort.Direction.DESC, "hourlyRate");
            case "name_asc" -> Sort.by(Sort.Direction.ASC, "user.name");
            case "name_desc" -> Sort.by(Sort.Direction.DESC, "user.name");
            default -> throw new IllegalArgumentException("Unsupported sort: " + sort);
        };
    }

    private Map<UUID, List<SkillResponse>> loadSkills(List<TutorProfile> profiles) {
        if (profiles.isEmpty()) {
            return Map.of();
        }
        List<UUID> userIds = profiles.stream()
                .map(profile -> profile.getUser().getId())
                .toList();

        List<SkillOffered> offered = skillOfferedRepository.findByTutorIdsWithSkill(userIds);
        return offered.stream()
                .collect(Collectors.groupingBy(
                        offer -> offer.getTutor().getId(),
                        Collectors.mapping(o -> SkillResponse.from(o.getSkill()), Collectors.toList())));
    }

    private TutorSearchResponse toResponse(TutorProfile profile, Map<UUID, List<SkillResponse>> skillsByUserId) {
        List<SkillResponse> skills = skillsByUserId.getOrDefault(profile.getUser().getId(), List.of())
                .stream()
                .sorted(java.util.Comparator.comparing(SkillResponse::name))
                .toList();

        return new TutorSearchResponse(
                profile.getId(),
                profile.getUser().getId(),
                profile.getUser().getName(),
                profile.getBio(),
                profile.getExperienceYears(),
                profile.getHourlyRate(),
                profile.getRating(),
                skills,
                profile.getCreatedAt());
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private String toLikePattern(String value) {
        String v = blankToNull(value);
        return (v == null) ? null : "%" + v.toLowerCase() + "%";
    }
}