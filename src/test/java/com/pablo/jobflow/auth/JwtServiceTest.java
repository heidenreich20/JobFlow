package com.pablo.jobflow.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        String secret = Base64.getEncoder().encodeToString(new byte[32]);
        jwtService = new JwtService(secret, 3_600_000);
    }

    @Test
    void generateToken_roundTripsEmail() {
        String token = jwtService.generateToken("user@example.com");

        assertThat(jwtService.extractEmail(token)).isEqualTo("user@example.com");
        assertThat(jwtService.isValid(token, "user@example.com")).isTrue();
    }

    @Test
    void isValid_whenEmailDoesNotMatch_returnsFalse() {
        String token = jwtService.generateToken("user@example.com");

        assertThat(jwtService.isValid(token, "other@example.com")).isFalse();
    }

    @Test
    void extractEmail_whenTokenExpired_throws() {
        Instant now = Instant.now();
        String token = jwtService.generateToken(
                "user@example.com",
                now.minusSeconds(120),
                now.minusSeconds(60));

        assertThatThrownBy(() -> jwtService.extractEmail(token))
                .isInstanceOf(ExpiredJwtException.class);
        assertThat(jwtService.isValid(token, "user@example.com")).isFalse();
    }

    @Test
    void extractEmail_whenTokenTampered_throws() {
        String token = jwtService.generateToken("user@example.com");
        String tampered = token.substring(0, token.length() - 4) + "xxxx";

        assertThatThrownBy(() -> jwtService.extractEmail(tampered))
                .isInstanceOf(JwtException.class);
        assertThat(jwtService.isValid(tampered, "user@example.com")).isFalse();
    }
}
