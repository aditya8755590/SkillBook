package com.skillbook.availability;

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

import java.time.DayOfWeek;
import java.time.LocalTime;

@Entity
@Table(name = "availability_rules", uniqueConstraints = @UniqueConstraint(name = "uk_availability_rules_tutor_day_start", columnNames = {"tutor_id", "day_of_week", "start_time"}))
@Getter
@Setter
@NoArgsConstructor
public class AvailabilityRule extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tutor_id", nullable = false)
    private User tutor;

    @Column(name = "day_of_week", nullable = false)
    private DayOfWeek dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(nullable = false, length = 64)
    private String timezone;

    @Column(nullable = false)
    private boolean active = true;

    public AvailabilityRule(User tutor, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime, String timezone) {
        this.tutor = tutor;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
        this.timezone = timezone;
    }
}