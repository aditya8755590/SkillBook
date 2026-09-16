package com.skillbook.availability;

import com.skillbook.common.BaseEntity;
import com.skillbook.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "availability_slots", uniqueConstraints = @UniqueConstraint(name = "uk_availability_slots_tutor_start", columnNames = {"tutor_id", "start_at"}))
@Getter
@Setter
@NoArgsConstructor
public class AvailabilitySlot extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tutor_id", nullable = false)
    private User tutor;

    @Column(name = "start_at", nullable = false)
    private OffsetDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private OffsetDateTime endAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AvailabilitySlotStatus status = AvailabilitySlotStatus.AVAILABLE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AvailabilitySlotSource source;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "rule_id")
    private AvailabilityRule rule;

    public AvailabilitySlot(User tutor, OffsetDateTime startAt, OffsetDateTime endAt, AvailabilitySlotSource source, AvailabilityRule rule) {
        this.tutor = tutor;
        this.startAt = startAt.withOffsetSameInstant(ZoneOffset.UTC);
        this.endAt = endAt.withOffsetSameInstant(ZoneOffset.UTC);
        this.source = source;
        this.rule = rule;
    }

    public UUID getRuleId() {
        return rule != null ? rule.getId() : null;
    }
}