package com.skillbook.skill;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SkillRepository extends JpaRepository<Skill, UUID> {

    Optional<Skill> findByName(String name);

    Optional<Skill> findByNameIgnoreCase(String name);

    java.util.List<Skill> findAllByOrderByNameAsc();

    java.util.List<Skill> findByNameContainingIgnoreCaseOrderByNameAsc(String name);
}