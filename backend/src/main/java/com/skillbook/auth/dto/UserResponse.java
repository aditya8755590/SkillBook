package com.skillbook.auth.dto;

import com.skillbook.common.Role;

import java.util.UUID;

public record UserResponse(UUID id, String email, String name, Role role) {
}