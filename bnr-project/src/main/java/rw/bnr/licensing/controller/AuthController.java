package rw.bnr.licensing.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import rw.bnr.licensing.audit.AuditService;
import rw.bnr.licensing.dto.*;
import rw.bnr.licensing.model.User;
import rw.bnr.licensing.repository.UserRepository;
import rw.bnr.licensing.security.JwtUtil;

/**
 * AuthController — handles login only.
 *
 * POST /api/auth/login
 *   → validates credentials via Spring's AuthenticationManager
 *   → generates JWT token
 *   → returns token + user info
 *
 * On failure → GlobalExceptionHandler catches BadCredentialsException
 *   → returns 401 with clean message (no stack trace)
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final AuditService auditService;

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {

        // Throws BadCredentialsException if invalid → caught by GlobalExceptionHandler
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        // Load full user for response details
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow();

        // Generate JWT
        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name());

        // Audit the login
        auditService.logLogin(user);

        log.info("User logged in: {} ({})", user.getEmail(), user.getRole());

        return ResponseEntity.ok(AuthResponse.builder()
                .token(token)
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .expiresIn(expirationMs)
                .build());
    }
}
