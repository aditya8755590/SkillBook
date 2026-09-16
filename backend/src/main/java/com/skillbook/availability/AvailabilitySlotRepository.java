package com.skillbook.availability;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface AvailabilitySlotRepository extends JpaRepository<AvailabilitySlot, UUID> {

    List<AvailabilitySlot> findByTutorIdAndStatusOrderByStartAtAsc(
            UUID tutorId, AvailabilitySlotStatus status);

    List<AvailabilitySlot> findByTutorIdAndStatusAndStartAtGreaterThanEqualAndStartAtLessThanEqualOrderByStartAtAsc(
            UUID tutorId, AvailabilitySlotStatus status, OffsetDateTime fromInclusive, OffsetDateTime toInclusive);

    boolean existsByTutorIdAndStartAt(UUID tutorId, OffsetDateTime startAt);

    void deleteByTutorId(UUID tutorId);
}