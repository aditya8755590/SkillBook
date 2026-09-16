package com.skillbook.availability;

import com.skillbook.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AvailabilityRuleRepository extends JpaRepository<AvailabilityRule, UUID> {

    List<AvailabilityRule> findByTutorIdOrderByDayOfWeekAscStartTimeAsc(UUID tutorId);

    List<AvailabilityRule> findByTutorIdAndActiveTrue(UUID tutorId);

    void deleteByTutorId(UUID tutorId);

    Optional<AvailabilityRule> findByTutorAndDayOfWeekAndStartTime(User tutor, DayOfWeek dayOfWeek, java.time.LocalTime startTime);
}