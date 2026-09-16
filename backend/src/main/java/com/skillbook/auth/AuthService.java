package com.skillbook.auth;

import com.skillbook.auth.dto.AuthResponse;
import com.skillbook.auth.dto.LoginRequest;
import com.skillbook.auth.dto.RegisterRequest;
import com.skillbook.auth.dto.UserResponse;
import com.skillbook.common.Role;
import com.skillbook.user.User;
import com.skillbook.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().toLowerCase().trim();

        if (userRepository.existsByEmail(email)) {
            throw new IllegalStateException("An account with this email already exists");
        }

        User user = new User();
        user.setEmail(email);
        user.setName(request.name().trim());
        user.setRole(request.role());
        user.setPasswordHash(passwordEncoder.encode(request.password()));

        User saved = userRepository.save(user);
        return buildAuthResponse(saved);
    }

    public AuthResponse login(LoginRequest request) {
        String email = request.email().toLowerCase().trim();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        return buildAuthResponse(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        UserResponse userResponse = new UserResponse(
                user.getId(), user.getEmail(), user.getName(), user.getRole());
        String role = user.getRole() == null ? Role.STUDENT.name() : user.getRole().name();
        String token = jwtService.generateToken(user.getEmail(), role);

        return new AuthResponse(token, "Bearer", jwtProperties.expirationMs(), userResponse);
    }
}