package com.skillbook.user;

import com.skillbook.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "student_profiles", uniqueConstraints = @UniqueConstraint(name = "uk_student_profiles_user", columnNames = "user_id"))
@Getter
@Setter
public class StudentProfile extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Size(max = 2000)
    @Column(length = 2000)
    private String bio;

    @Size(max = 50)
    @Column(name = "education_level", length = 50)
    private String educationLevel;
}