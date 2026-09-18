package com.skillbook.booking;

import com.skillbook.availability.AvailabilitySlot;
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
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "bookings", uniqueConstraints = @UniqueConstraint(name = "uk_bookings_slot", columnNames = "slot_id"))
@Getter
@Setter
@NoArgsConstructor
public class Booking extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "slot_id", nullable = false)
    private AvailabilitySlot slot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status = BookingStatus.CONFIRMED;

    @Size(max = 500)
    @Column(length = 500)
    private String message;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "refund_amount", precision = 8, scale = 2)
    private BigDecimal refundAmount;

    @Column(name = "cancellation_fee", precision = 8, scale = 2)
    private BigDecimal cancellationFee;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "cancelled_by")
    private User cancelledBy;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    public Booking(User student, AvailabilitySlot slot, String message, BigDecimal amount) {
        this.student = student;
        this.slot = slot;
        this.message = message;
        this.amount = amount;
    }
}