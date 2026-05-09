package rw.bnr.licensing.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import rw.bnr.licensing.exception.ResourceNotFoundException;
import rw.bnr.licensing.model.User;
import rw.bnr.licensing.repository.UserRepository;

/**
 * SecurityUtils — resolves the currently authenticated User entity.
 *
 * Spring Security holds the email (username) in the SecurityContext.
 * This utility loads the full User entity from the database.
 * Used by controllers to get the acting user for service calls.
 */
@Component
@RequiredArgsConstructor
public class SecurityUtils {

    private final UserRepository userRepository;

    public User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated()) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "No authenticated user found");
        }

        String email = auth.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Authenticated user not found in database: " + email));
    }
}
