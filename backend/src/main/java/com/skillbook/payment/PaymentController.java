package com.skillbook.payment;

import com.skillbook.common.ApiResponse;
import com.skillbook.payment.dto.CheckoutResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/bookings/{bookingId}/checkout")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<CheckoutResponse>> createCheckout(
            Authentication authentication,
            @PathVariable UUID bookingId) {
        CheckoutResponse response = paymentService.createCheckout(authentication.getName(), bookingId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping(value = "/payment/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Void>> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String signatureHeader) {
        paymentService.handleWebhook(payload, signatureHeader);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}