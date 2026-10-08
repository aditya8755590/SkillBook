package com.skillbook.booking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    List<Booking> findAllByStudentIdOrderByCreatedAtDesc(UUID studentId);

    Optional<Booking> findByIdAndStudentId(UUID id, UUID studentId);

    List<Booking> findByStatusAndSlot_EndAtBefore(BookingStatus status, OffsetDateTime endAtBefore);

    List<Booking> findByStatusAndSlot_StartAtBetweenAndReminderSentAtIsNull(
            BookingStatus status, OffsetDateTime startAtFrom, OffsetDateTime startAtTo);
}