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
 * SecurityConfig
 *
 * Architecture decision:
 * - HTML page routes (/applicant/**, /reviewer/**, etc.) are PERMITTED at the
 *   Spring Security URL level. The browser has no JWT when navigating directly.
 *   Page-level access control is handled by the JS in each page (redirects to
 *   /login if Auth.isLoggedIn() is false).
 *
 * - API routes (/api/**) are where REAL security enforcement happens:
 *   JWT filter + @PreAuthorize on every controller method. A user who calls
 *   the API without a valid token gets 401. Wrong role gets 403.
 *
 * - The 401/403 JSON handlers only fire for /api/** requests, so browser
 *   navigation never gets a raw JSON error response.
 *
 * This is the standard pattern for JWT + server-side rendered hybrid apps.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final UserDetailsServiceImpl userDetailsService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)

            // STATELESS — JWT only, no server-side sessions
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            .authorizeHttpRequests(auth -> auth
                // ── Public: auth API + static assets + ALL page routes ──────
                // Page routes are open because the browser has no JWT on direct
                // navigation. Real protection is in the JS + API @PreAuthorize.
                .requestMatchers(
                    "/api/auth/**",
                    "/login", "/logout", "/",
                    "/css/**", "/js/**", "/images/**",
                    "/error",
                    "/applicant/**",
                    "/reviewer/**",
                    "/approver/**",
                    "/admin/**"
                ).permitAll()

                // ── All /api/** routes require authentication ────────────────
                .requestMatchers("/api/**").authenticated()

                .anyRequest().authenticated()
            )

            // 401/403 handlers — only meaningful for /api/** calls
            // Browser page navigation never hits these because pages are permitted
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

            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}