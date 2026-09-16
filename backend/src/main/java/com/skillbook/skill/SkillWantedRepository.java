package com.skillbook.skill;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Set;
import java.util.UUID;

public interface SkillWantedRepository extends JpaRepository<SkillWanted, UUID> {

    Set<SkillWanted> findByStudentId(UUID studentId);

    boolean existsByStudentIdAndSkillId(UUID studentId, UUID skillId);
}