package com.skillbook.tutor;

import com.skillbook.common.BaseEntity;
import com.skillbook.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "tutor_profiles", uniqueConstraints = @UniqueConstraint(name = "uk_tutor_profiles_user", columnNames = "user_id"))
@Getter
@Setter
@NoArgsConstructor
public class TutorProfile extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Size(max = 2000)
    @Column(length = 2000)
    private String bio;

    @Min(0)
    @Column(name = "experience_years", nullable = false)
    private int experienceYears;

    @DecimalMin("0.0")
    @Digits(integer = 6, fraction = 2)
    @Column(name = "hourly_rate", precision = 8, scale = 2)
    private BigDecimal hourlyRate;

    @DecimalMin("0.0")
    @Digits(integer = 1, fraction = 2)
    @Column(precision = 3, scale = 2)
    private BigDecimal rating = BigDecimal.ZERO;
}