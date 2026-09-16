package com.skillbook.skill;

import com.skillbook.common.BaseEntity;
import com.skillbook.user.User;
import jakarta.persistence.Column;
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
@Table(name = "skill_offered", uniqueConstraints = @UniqueConstraint(name = "uk_skill_offered_tutor_skill", columnNames = {"tutor_id", "skill_id"}))
@Getter
@Setter
@NoArgsConstructor
public class SkillOffered extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tutor_id", nullable = false)
    private User tutor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    public SkillOffered(User tutor, Skill skill) {
        this.tutor = tutor;
        this.skill = skill;
    }
}