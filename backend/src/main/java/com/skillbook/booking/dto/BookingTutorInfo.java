package com.skillbook.booking.dto;

import com.skillbook.user.User;

import java.util.UUID;

public record BookingTutorInfo(
        UUID id,
        String name
) {
    public static BookingTutorInfo from(User tutor) {
        return new BookingTutorInfo(tutor.getId(), tutor.getName());
    }
}