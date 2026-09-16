package com.skillbook.auth.dto;

public record AuthResponse(String token, String tokenType, long expiresIn, UserResponse user) {
}