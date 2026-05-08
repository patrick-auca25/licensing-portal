package rw.bnr.licensing.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JwtAuthFilter — runs ONCE per request before Spring Security's own filters.
 *
 * What it does on every incoming request:
 * 1. Read the Authorization header
 * 2. Extract the JWT token (strip "Bearer " prefix)
 * 3. Validate the token (signature + expiry)
 * 4. Load the user from DB
 * 5. Set the authentication in Spring's SecurityContext
 *
 * If any step fails → SecurityContext stays empty →
 * Spring Security returns 401 Unauthorized automatically.
 *
 * If token is missing entirely (e.g. login page request) →
 * filter passes through without error — the endpoint's own
 * security rules decide if anonymous access is allowed.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        // No Authorization header or not a Bearer token — skip, let Spring decide
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Strip "Bearer " prefix (7 characters)
        final String token = authHeader.substring(7);

        // Invalid token — skip, SecurityContext stays empty → will get 401
        if (!jwtUtil.isTokenValid(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        final String email = jwtUtil.extractEmail(token);

        // Only set auth if not already authenticated in this request
        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = userDetailsService.loadUserByUsername(email);

            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,  // credentials — null after authentication
                            userDetails.getAuthorities()
                    );

            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            // Store in SecurityContext — Spring Security now knows who this user is
            // and what roles they have for @PreAuthorize checks
            SecurityContextHolder.getContext().setAuthentication(authToken);
        }

        filterChain.doFilter(request, response);
    }
}
