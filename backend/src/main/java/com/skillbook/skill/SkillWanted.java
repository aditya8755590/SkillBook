package com.skillbook.skill;

import com.skillbook.common.BaseEntity;
import com.skillbook.user.User;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "skill_wanted", uniqueConstraints = @UniqueConstraint(name = "uk_skill_wanted_student_skill", columnNames = {"student_id", "skill_id"}))
@Getter
@Setter
@NoArgsConstructor
public class SkillWanted extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    public SkillWanted(User student, Skill skill) {
        this.student = student;
        this.skill = skill;
    }
}