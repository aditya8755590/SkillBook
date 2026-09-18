package com.skillbook.booking;

import com.skillbook.booking.dto.BookingCreateRequest;
import com.skillbook.booking.dto.BookingResponse;
import com.skillbook.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<BookingResponse>> createBooking(
            Authentication authentication,
            @Valid @RequestBody BookingCreateRequest request) {
        BookingResponse response = bookingService.createBooking(authentication.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @GetMapping
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<List<BookingResponse>>> listMyBookings(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok(bookingService.listMyBookings(authentication.getName())));
    }

    @GetMapping("/{bookingId}")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<BookingResponse>> getBooking(
            Authentication authentication,
            @PathVariable UUID bookingId) {
        return ResponseEntity.ok(ApiResponse.ok(bookingService.getBooking(authentication.getName(), bookingId)));
    }

    @PostMapping("/{bookingId}/cancel")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<BookingResponse>> cancelBooking(
            Authentication authentication,
            @PathVariable UUID bookingId) {
        return ResponseEntity.ok(ApiResponse.ok(bookingService.cancelBooking(authentication.getName(), bookingId)));
    }
}