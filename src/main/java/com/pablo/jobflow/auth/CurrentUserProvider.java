package com.pablo.jobflow.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.pablo.jobflow.user.User;

/**
 * Resolves the authenticated {@link User} from the security context.
 *
 * <p>The returned entity is <strong>detached</strong>: it was loaded by
 * {@link JpaUserDetailsService} during authentication, outside any transaction.
 * It is safe to read, to compare, and to assign to a {@code @ManyToOne}
 * association before saving (Hibernate only needs its identifier to write the
 * foreign key). Mutating it will <em>not</em> persist anything.
 */
@Component
public class CurrentUserProvider {

    public User requireCurrentUser() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException(
                    "No authenticated user in the security context");
        }

        Object principal = authentication.getPrincipal();

        if (!(principal instanceof JobflowUserDetails userDetails)) {
            throw new IllegalStateException(
                    "Unexpected principal type: " + principal.getClass().getName());
        }

        return userDetails.getUser();
    }
}
