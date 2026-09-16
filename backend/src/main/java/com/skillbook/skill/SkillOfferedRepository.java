package com.skillbook.skill;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Set;
import java.util.UUID;

public interface SkillOfferedRepository extends JpaRepository<SkillOffered, UUID> {

    Set<SkillOffered> findByTutorId(UUID tutorId);

    boolean existsByTutorIdAndSkillId(UUID tutorId, UUID skillId);
}