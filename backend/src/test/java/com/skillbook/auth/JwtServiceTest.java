package com.skillbook.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "test-secret-for-jwt-signing-must-be-at-least-32-chars";

    private JwtService service() {
        return new JwtService(new JwtProperties(SECRET, 86_400_000L));
    }

    @Test
    void generatesTokenThatParsesBackToClaims() {
        String token = service().generateToken("alice@example.com", "TUTOR");

        Claims claims = service().parseToken(token);

        assertThat(claims.getSubject()).isEqualTo("alice@example.com");
        assertThat(claims.get("role", String.class)).isEqualTo("TUTOR");
        assertThat(claims.getExpiration()).isAfterOrEqualTo(claims.getIssuedAt());
    }

    @Test
    void rejectsExpiredToken() {
        JwtService fastExpiring = new JwtService(new JwtProperties(SECRET, 1L));
        String token = fastExpiring.generateToken("bob@example.com", "STUDENT");

        try {
            Thread.sleep(5);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        assertThatThrownBy(() -> fastExpiring.parseToken(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void rejectsTamperedToken() {
        String token = service().generateToken("carol@example.com", "ADMIN");
        String tampered = token.substring(0, token.length() - 2) + "zz";

        assertThatThrownBy(() -> service().parseToken(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTokenSignedWithDifferentSecret() {
        String token = service().generateToken("dave@example.com", "STUDENT");

        JwtService otherService = new JwtService(new JwtProperties(
                "a-completely-different-secret-that-is-still-at-least-32-chars", 86_400_000L));

        assertThatThrownBy(() -> otherService.parseToken(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsGarbageInput() {
        assertThatThrownBy(() -> service().parseToken("not-a-jwt"))
                .isInstanceOf(JwtException.class);
    }
}