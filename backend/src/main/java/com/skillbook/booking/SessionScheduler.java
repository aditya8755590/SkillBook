package com.skillbook.booking;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Thin trigger for the session lifecycle. The actual state transitions live in
 * {@link BookingService} so tests can invoke them deterministically; this bean
 * only schedules the calls and is switched off in the test profile
 * ({@code app.scheduler.enabled=false}) so background jobs never race the suite.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class SessionScheduler {

    private final BookingService bookingService;

    @Scheduled(fixedDelayString = "PT1M")
    public void completeFinishedSessions() {
        bookingService.completeExpiredBookings();
    }

    @Scheduled(fixedDelayString = "PT5M")
    public void sendUpcomingSessionReminders() {
        bookingService.sendSessionReminders();
    }
}
