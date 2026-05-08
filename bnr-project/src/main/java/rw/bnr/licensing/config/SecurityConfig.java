package rw.bnr.licensing.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import rw.bnr.licensing.security.*;

/**
 * SecurityConfig — the central security configuration.
 *
 * Key decisions explained:
 *
 * 1. STATELESS session — JWT is stateless so we tell Spring NOT to create
 *    HTTP sessions. Every request must carry its own JWT.
 *
 * 2. CSRF disabled — CSRF protection is for session-based auth where a browser
 *    automatically sends cookies. With JWT in Authorization header, CSRF is
 *    not applicable. Disabling it avoids breaking the REST API.
 *
 * 3. @EnableMethodSecurity — enables @PreAuthorize annotations on controller
 *    methods. This is where role enforcement happens at the METHOD level,
 *    not just URL level. A user who bypasses the frontend is still denied here.
 *
 * 4. Public endpoints — /api/auth/** (login) and static assets are open.
 *    Everything else requires authentication.
 *
 * 5. JwtAuthFilter runs BEFORE UsernamePasswordAuthenticationFilter —
 *    this ensures JWT is validated before Spring's default auth kicks in.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity           // enables @PreAuthorize on controller methods
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final UserDetailsServiceImpl userDetailsService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF — not needed for stateless JWT API
            .csrf(AbstractHttpConfigurer::disable)

            // Stateless — no HTTP session created or used
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // URL-level authorization rules
            .authorizeHttpRequests(auth -> auth
                // Public endpoints
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/login", "/css/**", "/js/**", "/error").permitAll()
                // Everything else requires authentication
                .anyRequest().authenticated()
            )

            // Custom 401 and 403 responses — no redirects for REST API calls
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(401);
                    response.setContentType("application/json");
                    response.getWriter().write(
                        "{\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}"
                    );
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(403);
                    response.setContentType("application/json");
                    response.getWriter().write(
                        "{\"error\":\"Forbidden\",\"message\":\"You do not have permission to perform this action\"}"
                    );
                })
            )

            // Register JWT filter before Spring's default auth filter
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // ── Password encoder — BCrypt strength 12 ───────────────────────────
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    // ── Authentication provider — ties UserDetailsService + PasswordEncoder
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    // ── AuthenticationManager — used by AuthController to authenticate login
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
