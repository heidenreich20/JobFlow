package com.pablo.jobflow.auth;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.pablo.jobflow.user.User;

class CurrentUserProviderTest {

    private final CurrentUserProvider provider = new CurrentUserProvider();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void requireCurrentUser_whenPrincipalIsJobflowUserDetails_returnsUser() {
        User user = new User();
        user.setEmail("actor@acme.com");
        JobflowUserDetails principal = new JobflowUserDetails(user);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities()));

        assertSame(user, provider.requireCurrentUser());
    }

    @Test
    void requireCurrentUser_whenNoAuthentication_throws() {
        assertThrows(IllegalStateException.class, provider::requireCurrentUser);
    }

    @Test
    void requireCurrentUser_whenPrincipalIsNotJobflowUserDetails_throws() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "actor@acme.com", null, List.of()));

        assertThrows(IllegalStateException.class, provider::requireCurrentUser);
    }
}
