package com.skillbook.booking.dto;

import com.skillbook.user.User;

import java.util.UUID;

public record BookingStudentInfo(
        UUID id,
        String name
) {
    public static BookingStudentInfo from(User student) {
        return new BookingStudentInfo(student.getId(), student.getName());
    }
}