package com.rewatch.security;

import java.security.Key;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Issues and parses the JWTs that back stateless auth. The token subject is the
 * user id (not the username/email) so every downstream lookup is by primary key.
 */
@Component
public class JwtService {

    private final Key signingKey;
    private final long expirationMs;

    public JwtService(@Value("${jwt.secret}") String secret,
                      @Value("${jwt.expiration-ms}") long expirationMs) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    /**
     * @param tokenVersion the issuing user's User.tokenVersion at issue time, embedded
     *                     as the "tv" claim. JwtAuthFilter rejects the token once this
     *                     no longer matches the live column — see User.tokenVersion's
     *                     javadoc for why this is the app's whole revocation mechanism.
     */
    public String issue(Long userId, String username, int tokenVersion) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("tv", tokenVersion)
                .issuedAt(now)
                .expiration(expiry)
                .signWith((SecretKey) signingKey)
                .compact();
    }

    public record ValidatedToken(Long userId, int tokenVersion) {}

    /** Returns the parsed userId+tokenVersion, or null if the token is missing/expired/invalid. */
    public ValidatedToken validate(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith((SecretKey) signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Integer tv = claims.get("tv", Integer.class);
            return new ValidatedToken(Long.valueOf(claims.getSubject()), tv == null ? 0 : tv);
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
}
