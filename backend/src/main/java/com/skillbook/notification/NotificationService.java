package com.skillbook.notification;

import com.skillbook.booking.Booking;
import com.skillbook.exception.ResourceNotFoundException;
import com.skillbook.notification.dto.NotificationResponse;
import com.skillbook.user.User;
import com.skillbook.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * In-app notification persistence plus event fan-out. Booking and payment
 * domains call the {@code notifyX} methods; this service never depends on
 * them back (entity reads only), so there is no bean cycle.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("MMM d, HH:mm 'UTC'");

    private static final String SUBJECT_CONFIRMED = "Booking confirmed — SkillBook";
    private static final String SUBJECT_CANCELLED = "Booking cancelled — SkillBook";
    private static final String SUBJECT_RECEIPT = "Payment received — SkillBook";
    private static final String SUBJECT_REFUND = "Refund issued — SkillBook";
    private static final String SUBJECT_REMINDER = "Session reminder — SkillBook";

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final MailService mailService;

    // ---------- queries ----------

    @Transactional(readOnly = true)
    public List<NotificationResponse> list(String email) {
        User user = findUser(email);
        return notificationRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(String email) {
        User user = findUser(email);
        return notificationRepository.countByUserIdAndReadFalse(user.getId());
    }

    @Transactional
    public NotificationResponse markRead(String email, UUID id) {
        User user = findUser(email);
        Notification notification = notificationRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Notification", "id", id));
        notification.setRead(true);
        return NotificationResponse.from(notification);
    }

    @Transactional
    public int markAllRead(String email) {
        User user = findUser(email);
        int marked = 0;
        for (Notification notification : notificationRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId())) {
            if (!notification.isRead()) {
                notification.setRead(true);
                marked++;
            }
        }
        return marked;
    }

    // ---------- event fan-out ----------

    @Transactional
    public void notifyBookingCreated(Booking booking) {
        User student = booking.getStudent();
        User tutor = booking.getSlot().getTutor();
        String when = formatRange(booking);

        create(student, NotificationType.BOOKING_CONFIRMED, booking,
                "Your session with " + tutor.getName() + " is confirmed for " + when + ".");
        create(tutor, NotificationType.NEW_BOOKING, booking,
                student.getName() + " booked your session on " + when + ".");
        email(student, SUBJECT_CONFIRMED,
                "Your session with " + tutor.getName() + " is confirmed for " + when + ".");
    }

    @Transactional
    public void notifyBookingCancelled(Booking booking) {
        User student = booking.getStudent();
        User tutor = booking.getSlot().getTutor();
        String when = formatRange(booking);

        create(student, NotificationType.BOOKING_CANCELLED, booking,
                "Your session with " + tutor.getName() + " on " + when + " was cancelled.");
        create(tutor, NotificationType.BOOKING_CANCELLED, booking,
                "Your session with " + student.getName() + " on " + when + " was cancelled.");
        email(student, SUBJECT_CANCELLED,
                "Your session with " + tutor.getName() + " on " + when + " was cancelled.");
    }

    @Transactional
    public void notifyPaymentReceived(Booking booking, BigDecimal amount) {
        User student = booking.getStudent();
        String message = "Payment of $" + amount.toPlainString()
                + " received for your session on " + formatStart(booking) + ".";

        create(student, NotificationType.PAYMENT_RECEIPT, booking, message);
        email(student, SUBJECT_RECEIPT, message);
    }

    @Transactional
    public void notifyPaymentRefunded(Booking booking, BigDecimal amount) {
        User student = booking.getStudent();
        String message = "A refund of $" + amount.toPlainString()
                + " was issued for your session on " + formatStart(booking) + ".";

        create(student, NotificationType.PAYMENT_REFUNDED, booking, message);
        email(student, SUBJECT_REFUND, message);
    }

    @Transactional
    public void notifySessionCompleted(Booking booking) {
        User student = booking.getStudent();
        User tutor = booking.getSlot().getTutor();

        create(student, NotificationType.SESSION_COMPLETED, booking,
                "Your session with " + tutor.getName() + " has ended.");
        create(tutor, NotificationType.SESSION_COMPLETED, booking,
                "Your session with " + student.getName() + " has ended.");
    }

    @Transactional
    public void notifySessionReminder(Booking booking) {
        User student = booking.getStudent();
        User tutor = booking.getSlot().getTutor();
        String when = formatStart(booking);

        create(student, NotificationType.SESSION_REMINDER, booking,
                "Reminder: your session with " + tutor.getName() + " starts " + when + ".");
        create(tutor, NotificationType.SESSION_REMINDER, booking,
                "Reminder: your session with " + student.getName() + " starts " + when + ".");
        email(student, SUBJECT_REMINDER,
                "Your session with " + tutor.getName() + " starts " + when + ".");
        email(tutor, SUBJECT_REMINDER,
                "Your session with " + student.getName() + " starts " + when + ".");
    }

    // ---------- helpers ----------

    private void create(User recipient, NotificationType type, Booking booking, String message) {
        notificationRepository.save(new Notification(recipient, booking, type, truncate(message)));
    }

    private void email(User recipient, String subject, String message) {
        mailService.send(recipient.getEmail(), subject,
                "Hi " + recipient.getName() + ",\n\n" + message + "\n\n— SkillBook");
    }

    private String formatRange(Booking booking) {
        return formatStart(booking) + " – " + formatEnd(booking);
    }

    private String formatStart(Booking booking) {
        return TIME.format(booking.getSlot().getStartAt().withOffsetSameInstant(ZoneOffset.UTC));
    }

    private String formatEnd(Booking booking) {
        return TIME.format(booking.getSlot().getEndAt().withOffsetSameInstant(ZoneOffset.UTC));
    }

    private String truncate(String message) {
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }
}
