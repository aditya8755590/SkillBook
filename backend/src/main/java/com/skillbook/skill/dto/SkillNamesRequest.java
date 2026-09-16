package com.skillbook.skill.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SkillNamesRequest(
    @Size(min = 0, max = 50) List<@NotBlank @Size(max = 100) String> skills
) {}
