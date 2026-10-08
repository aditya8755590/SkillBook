package com.skillbook.notification;

import com.skillbook.common.ApiResponse;
import com.skillbook.notification.dto.NotificationResponse;
import com.skillbook.notification.dto.UnreadCountResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> list(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok(notificationService.list(authentication.getName())));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<UnreadCountResponse>> unreadCount(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok(
                new UnreadCountResponse(notificationService.unreadCount(authentication.getName()))));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<ApiResponse<NotificationResponse>> markRead(
            Authentication authentication,
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(notificationService.markRead(authentication.getName(), id)));
    }

    @PostMapping("/read-all")
    public ResponseEntity<ApiResponse<UnreadCountResponse>> markAllRead(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok(
                new UnreadCountResponse(notificationService.markAllRead(authentication.getName()))));
    }
}
