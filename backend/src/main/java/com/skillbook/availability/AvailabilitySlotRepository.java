package com.skillbook.availability;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AvailabilitySlotRepository extends JpaRepository<AvailabilitySlot, UUID> {

    List<AvailabilitySlot> findByTutorIdAndStatusOrderByStartAtAsc(
            UUID tutorId, AvailabilitySlotStatus status);

    List<AvailabilitySlot> findByTutorIdAndStatusAndStartAtGreaterThanEqualAndStartAtLessThanEqualOrderByStartAtAsc(
            UUID tutorId, AvailabilitySlotStatus status, OffsetDateTime fromInclusive, OffsetDateTime toInclusive);

    boolean existsByTutorIdAndStartAt(UUID tutorId, OffsetDateTime startAt);

    void deleteByTutorId(UUID tutorId);

    @Query(value = "select * from availability_slots where id = :id for update", nativeQuery = true)
    Optional<AvailabilitySlot> findByIdForUpdate(@Param("id") UUID id);
}