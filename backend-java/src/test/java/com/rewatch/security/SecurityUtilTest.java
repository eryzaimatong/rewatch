package com.rewatch.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import jakarta.servlet.http.HttpServletRequest;

/**
 * SecurityUtil.trustProxyHeaders is static, process-global mutable state
 * (see SecurityUtilConfig) — reset it after every test so one test's
 * setting can't leak into the next.
 */
class SecurityUtilTest {

    @AfterEach
    void resetTrustProxyHeaders() {
        SecurityUtil.setTrustProxyHeaders(false);
    }

    private HttpServletRequest requestWith(String remoteAddr, String forwardedFor) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn(remoteAddr);
        when(request.getHeader("X-Forwarded-For")).thenReturn(forwardedFor);
        return request;
    }

    @Test
    void ignoresXForwardedForByDefault() {
        // trustProxyHeaders defaults false — a request straight from the
        // internet (no reverse proxy in front) always resolves to
        // getRemoteAddr(), never a client-supplied header.
        HttpServletRequest request = requestWith("203.0.113.9", "1.2.3.4");

        assertThat(SecurityUtil.clientIp(request)).isEqualTo("203.0.113.9");
    }

    @Test
    void takesTheRightmostHopWhenTrusted() {
        SecurityUtil.setTrustProxyHeaders(true);
        // Render appends its own observed peer to the END of the header
        // rather than overwriting it — the real, proxy-observed address is
        // always the last entry, not the first.
        HttpServletRequest request = requestWith("10.0.0.1", "9.9.9.9, 198.51.100.20");

        assertThat(SecurityUtil.clientIp(request)).isEqualTo("198.51.100.20");
    }

    @Test
    void aClientForgingTheLeftmostEntryOnEveryRequestStillResolvesToTheSameBucket() {
        // The actual finding this test exists to close: split(",")[0] would
        // let an attacker rotate the leftmost, client-controlled entry on
        // every request and get a fresh rate-limit bucket each time,
        // defeating RateLimiterService entirely. Reading the rightmost
        // (proxy-observed) entry instead means the attacker's forged prefix
        // never changes what clientIp() returns.
        SecurityUtil.setTrustProxyHeaders(true);
        String realProxyObservedIp = "198.51.100.20";

        String attempt1 = SecurityUtil.clientIp(requestWith("10.0.0.1", "1.1.1.1, " + realProxyObservedIp));
        String attempt2 = SecurityUtil.clientIp(requestWith("10.0.0.1", "2.2.2.2, " + realProxyObservedIp));
        String attempt3 = SecurityUtil.clientIp(requestWith("10.0.0.1", "203.0.113.250, " + realProxyObservedIp));

        assertThat(attempt1).isEqualTo(realProxyObservedIp);
        assertThat(attempt2).isEqualTo(realProxyObservedIp);
        assertThat(attempt3).isEqualTo(realProxyObservedIp);
    }

    @Test
    void aSingleForgedHopWithNoRealProxyInFrontStillResolvesToWhatItClaims() {
        // Documents the actual boundary of this protection: it defends
        // against an attacker manipulating entries a real trusted proxy
        // appended AFTER, not against enabling trust-proxy-headers on a
        // deployment that isn't actually behind one. With exactly one
        // (forged) hop, rightmost === that forged value — this is why
        // TRUST_PROXY_HEADERS must only be true when a real proxy sits in
        // front and unconditionally appends.
        SecurityUtil.setTrustProxyHeaders(true);
        HttpServletRequest request = requestWith("10.0.0.1", "9.9.9.9");

        assertThat(SecurityUtil.clientIp(request)).isEqualTo("9.9.9.9");
    }

    @Test
    void fallsBackToRemoteAddrWhenTrustedButHeaderIsAbsent() {
        SecurityUtil.setTrustProxyHeaders(true);
        HttpServletRequest request = requestWith("203.0.113.9", null);

        assertThat(SecurityUtil.clientIp(request)).isEqualTo("203.0.113.9");
    }

    @Test
    void handlesWhitespaceAroundEachHop() {
        SecurityUtil.setTrustProxyHeaders(true);
        HttpServletRequest request = requestWith("10.0.0.1", "1.1.1.1 ,  198.51.100.20  ");

        assertThat(SecurityUtil.clientIp(request)).isEqualTo("198.51.100.20");
    }
}
