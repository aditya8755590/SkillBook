package com.skillbook.booking;

import com.skillbook.availability.AvailabilitySlot;
import com.skillbook.availability.AvailabilitySlotRepository;
import com.skillbook.availability.AvailabilitySlotStatus;
import com.skillbook.booking.dto.BookingCreateRequest;
import com.skillbook.booking.dto.BookingResponse;
import com.skillbook.common.Role;
import com.skillbook.exception.ResourceNotFoundException;
import com.skillbook.payment.PaymentService;
import com.skillbook.tutor.TutorProfile;
import com.skillbook.tutor.TutorProfileRepository;
import com.skillbook.user.User;
import com.skillbook.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingService {

    private static final int FULL_REFUND_WINDOW_HOURS = 24;
    private static final BigDecimal PARTIAL_FEE_RATE = new BigDecimal("0.50");
    private static final int MINUTES_PER_HOUR = 60;

    private final UserRepository userRepository;
    private final AvailabilitySlotRepository slotRepository;
    private final TutorProfileRepository tutorProfileRepository;
    private final BookingRepository bookingRepository;
    private final PaymentService paymentService;

    @Transactional
    public BookingResponse createBooking(String email, BookingCreateRequest request) {
        User student = findStudentByEmail(email);
        AvailabilitySlot slot = slotRepository.findByIdForUpdate(request.slotId())
                .orElseThrow(() -> new ResourceNotFoundException("AvailabilitySlot", "id", request.slotId()));

        if (slot.getStatus() != AvailabilitySlotStatus.AVAILABLE) {
            throw new IllegalStateException("Slot is no longer available");
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (!slot.getStartAt().isAfter(now)) {
            throw new IllegalArgumentException("Cannot book a slot that has already started");
        }

        BigDecimal amount = computeAmount(slot);

        Booking booking = new Booking(student, slot, request.message(), amount);
        booking = bookingRepository.save(booking);
        slot.setStatus(AvailabilitySlotStatus.BOOKED);
        return withPayment(booking);
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> listMyBookings(String email) {
        User student = findStudentByEmail(email);
        return bookingRepository.findAllByStudentIdOrderByCreatedAtDesc(student.getId()).stream()
                .map(this::withPayment)
                .toList();
    }

    @Transactional(readOnly = true)
    public BookingResponse getBooking(String email, UUID bookingId) {
        User student = findStudentByEmail(email);
        return bookingRepository.findByIdAndStudentId(bookingId, student.getId())
                .map(this::withPayment)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", "id", bookingId));
    }

    @Transactional
    public BookingResponse cancelBooking(String email, UUID bookingId) {
        User student = findStudentByEmail(email);
        Booking booking = bookingRepository.findByIdAndStudentId(bookingId, student.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking", "id", bookingId));
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new IllegalStateException("Booking is not cancellable");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (!now.isBefore(booking.getSlot().getStartAt())) {
            throw new IllegalStateException("Cannot cancel a session that has already started");
        }

        BigDecimal fee;
        BigDecimal refund;
        if (now.isBefore(booking.getSlot().getStartAt().minusHours(FULL_REFUND_WINDOW_HOURS))) {
            fee = BigDecimal.ZERO.setScale(2);
            refund = booking.getAmount();
        } else {
            fee = booking.getAmount().multiply(PARTIAL_FEE_RATE).setScale(2, RoundingMode.HALF_UP);
            refund = booking.getAmount().subtract(fee);
        }

        booking.setCancellationFee(fee);
        booking.setRefundAmount(refund);
        booking.setCancelledAt(now);
        booking.setCancelledBy(student);
        booking.setStatus(BookingStatus.CANCELLED);
        booking.getSlot().setStatus(AvailabilitySlotStatus.AVAILABLE);
        paymentService.refundForCancelIfPaid(booking);
        return withPayment(booking);
    }

    @Transactional
    public void completeExpiredBookings() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        for (Booking booking : bookingRepository.findByStatusAndSlot_EndAtBefore(BookingStatus.CONFIRMED, now)) {
            booking.setStatus(BookingStatus.COMPLETED);
            booking.setCompletedAt(now);
        }
    }

    private BigDecimal computeAmount(AvailabilitySlot slot) {
        UUID tutorId = slot.getTutor().getId();
        TutorProfile profile = tutorProfileRepository.findByUser_Id(tutorId)
                .orElseThrow(() -> new IllegalStateException("Tutor has no profile"));
        if (profile.getHourlyRate() == null) {
            throw new IllegalArgumentException("Tutor has not set an hourly rate");
        }
        BigDecimal hours = BigDecimal.valueOf(Duration.between(slot.getStartAt(), slot.getEndAt()).toMinutes())
                .divide(BigDecimal.valueOf(MINUTES_PER_HOUR), 4, RoundingMode.HALF_UP);
        return profile.getHourlyRate()
                .multiply(hours)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BookingResponse withPayment(Booking booking) {
        return BookingResponse.from(
                booking,
                paymentService.paymentInfoForBooking(booking.getId()).orElse(null)
        );
    }

    private User findStudentByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
        if (user.getRole() != Role.STUDENT) {
            throw new IllegalArgumentException("Only students can create bookings");
        }
        return user;
    }
}