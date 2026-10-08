package com.skillbook.notification.dto;

import com.skillbook.notification.Notification;

import java.util.UUID;

public record NotificationResponse(
        UUID id,
        String type,
        String message,
        boolean read,
        UUID bookingId,
        String createdAt
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType().name(),
                notification.getMessage(),
                notification.isRead(),
                notification.getBooking() != null ? notification.getBooking().getId() : null,
                notification.getCreatedAt() != null ? notification.getCreatedAt().toString() : null
        );
    }
}
