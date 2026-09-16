package com.skillbook.availability;

import com.skillbook.availability.dto.AvailabilityRuleResponse;
import com.skillbook.availability.dto.AvailabilityRulesRequest;
import com.skillbook.availability.dto.AvailabilitySlotResponse;
import com.skillbook.availability.dto.OneOffSlotRequest;
import com.skillbook.availability.dto.SlotGenerationRequest;
import com.skillbook.availability.dto.SlotGenerationResponse;
import com.skillbook.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/tutors")
@RequiredArgsConstructor
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    @PutMapping("/availability/rules")
    @PreAuthorize("hasRole('TUTOR')")
    public ResponseEntity<ApiResponse<List<AvailabilityRuleResponse>>> replaceRules(
            Authentication authentication,
            @Valid @RequestBody AvailabilityRulesRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(availabilityService.replaceRules(authentication.getName(), request)));
    }

    @GetMapping("/availability/rules")
    @PreAuthorize("hasRole('TUTOR')")
    public ResponseEntity<ApiResponse<List<AvailabilityRuleResponse>>> listRules(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok(availabilityService.listRules(authentication.getName())));
    }

    @PostMapping("/availability/slots")
    @PreAuthorize("hasRole('TUTOR')")
    public ResponseEntity<ApiResponse<AvailabilitySlotResponse>> addOneOffSlot(
            Authentication authentication,
            @Valid @RequestBody OneOffSlotRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(availabilityService.addOneOffSlot(authentication.getName(), request)));
    }

    @DeleteMapping("/availability/slots/{slotId}")
    @PreAuthorize("hasRole('TUTOR')")
    public ResponseEntity<ApiResponse<Void>> deleteOneOffSlot(Authentication authentication, @PathVariable UUID slotId) {
        availabilityService.deleteOneOffSlot(authentication.getName(), slotId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/availability/generate")
    @PreAuthorize("hasRole('TUTOR')")
    public ResponseEntity<ApiResponse<SlotGenerationResponse>> generateSlots(
            Authentication authentication,
            @Valid @RequestBody SlotGenerationRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(availabilityService.generateSlots(authentication.getName(), request)));
    }

    @GetMapping("/{userId}/availability")
    public ResponseEntity<ApiResponse<List<AvailabilitySlotResponse>>> listTutorAvailability(
            @PathVariable UUID userId,
            @RequestParam String from,
            @RequestParam String to) {
        return ResponseEntity.ok(ApiResponse.ok(availabilityService.listTutorAvailability(userId, from, to)));
    }
}