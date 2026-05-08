package rw.bnr.licensing.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JwtUtil — responsible for:
 * 1. Generating a signed JWT token on login
 * 2. Extracting claims (email, role) from a token
 * 3. Validating token signature and expiry
 *
 * Token structure:
 *   Header:  { alg: HS256 }
 *   Payload: { sub: email, role: REVIEWER, iat: ..., exp: ... }
 *   Signature: HMAC-SHA256(header.payload, secret)
 *
 * The payload is Base64-encoded NOT encrypted.
 * Never store sensitive data (passwords, PINs) in the payload.
 */
@Component
public class JwtUtil {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration-ms}")
    private long jwtExpirationMs;

    // ── Build the signing key from config secret ─────────────────────────
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    // ── Generate token ───────────────────────────────────────────────────
    public String generateToken(String email, String role) {
        Date now    = new Date();
        Date expiry = new Date(now.getTime() + jwtExpirationMs);

        return Jwts.builder()
                .subject(email)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey())
                .compact();
    }

    // ── Extract email (subject) from token ──────────────────────────────
    public String extractEmail(String token) {
        return parseClaims(token).getSubject();
    }

    // ── Extract role from token ──────────────────────────────────────────
    public String extractRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    // ── Validate token ───────────────────────────────────────────────────
    // Returns true only if signature is valid AND token is not expired
    public boolean isTokenValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    // ── Parse and verify signature + expiry ─────────────────────────────
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
