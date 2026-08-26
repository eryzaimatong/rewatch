package com.rewatch.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Unit-level pin on JwtService's own contract, underneath SecurityConfigTest's
 * end-to-end coverage of the same failure modes through the real filter chain
 * — this is what actually fails first (and fastest) if the token format,
 * expiry math, or tokenVersion embedding ever regresses.
 */
class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-at-least-256-bits-long-for-hs256-000000";

    private JwtService service(long expirationMs) {
        return new JwtService(SECRET, expirationMs);
    }

    @Test
    void issueThenValidateRoundTripsTheUserIdAndTokenVersion() {
        JwtService jwt = service(60_000);

        String token = jwt.issue(42L, "someone", 7);
        JwtService.ValidatedToken parsed = jwt.validate(token);

        assertThat(parsed).isNotNull();
        assertThat(parsed.userId()).isEqualTo(42L);
        assertThat(parsed.tokenVersion()).isEqualTo(7);
    }

    @Test
    void anExpiredTokenFailsToValidate() {
        JwtService alreadyExpired = service(-1_000);

        String token = alreadyExpired.issue(1L, "someone", 0);

        assertThat(service(60_000).validate(token)).isNull();
    }

    @Test
    void aTokenSignedWithADifferentSecretFailsToValidate() {
        JwtService issuer = new JwtService("a-completely-different-secret-that-is-also-256-bits-000", 60_000);
        String token = issuer.issue(1L, "someone", 0);

        assertThat(service(60_000).validate(token)).isNull();
    }

    @Test
    void garbageInputFailsToValidateRatherThanThrowing() {
        assertThat(service(60_000).validate("not-a-jwt-at-all")).isNull();
        assertThat(service(60_000).validate("")).isNull();
    }

    @Test
    void aTokenWithNoTvClaimDefaultsToTokenVersionZero() {
        // Defensive branch in validate(): claims.get("tv", ...) can come
        // back null for a token that predates the "tv" claim existing, or
        // one built by hand — must not NPE unboxing it.
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String tokenWithNoTvClaim = Jwts.builder()
                .subject("5")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();

        JwtService.ValidatedToken parsed = service(60_000).validate(tokenWithNoTvClaim);

        assertThat(parsed).isNotNull();
        assertThat(parsed.userId()).isEqualTo(5L);
        assertThat(parsed.tokenVersion()).isZero();
    }
}
