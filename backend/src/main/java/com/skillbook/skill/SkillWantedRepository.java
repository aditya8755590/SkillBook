package com.skillbook.skill;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Set;
import java.util.UUID;

public interface SkillWantedRepository extends JpaRepository<SkillWanted, UUID> {

    Set<SkillWanted> findByStudentId(UUID studentId);

    boolean existsByStudentIdAndSkillId(UUID studentId, UUID skillId);

    @Query("select sw from SkillWanted sw join fetch sw.skill where sw.student.id = :studentId")
    Set<SkillWanted> findByStudentIdWithSkill(@Param("studentId") UUID studentId);

    long deleteByStudentId(UUID studentId);
}