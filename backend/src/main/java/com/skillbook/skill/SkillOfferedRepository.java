package com.skillbook.skill;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Set;
import java.util.UUID;

public interface SkillOfferedRepository extends JpaRepository<SkillOffered, UUID> {

    Set<SkillOffered> findByTutorId(UUID tutorId);

    boolean existsByTutorIdAndSkillId(UUID tutorId, UUID skillId);

    @Query("select so from SkillOffered so join fetch so.skill where so.tutor.id = :tutorId")
    Set<SkillOffered> findByTutorIdWithSkill(@Param("tutorId") UUID tutorId);

    long deleteByTutorId(UUID tutorId);

    @Query("select so from SkillOffered so join fetch so.skill where so.tutor.id in :tutorIds")
    java.util.List<SkillOffered> findByTutorIdsWithSkill(@Param("tutorIds") java.util.Collection<UUID> tutorIds);
}