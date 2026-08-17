package com.rewatch.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Controllers take an {id}/{userId} path or body parameter *and* run behind
 * JwtAuthFilter, which authenticates the caller as a user id. This bridges the
 * two: every endpoint that reads or writes one user's data must call
 * {@link #requireSelf} so a valid token for user A can never touch user B's rows
 * just by changing the id in the URL or request body.
 */
public final class SecurityUtil {

    private SecurityUtil() {}

    /** The authenticated caller's user id, or null on an anonymous request. */
    public static Long currentUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        return principal instanceof Long id ? id : null;
    }

    /** Throws 401/403 unless the authenticated caller *is* the resource owner. */
    public static void requireSelf(Authentication authentication, Long resourceUserId) {
        Long callerId = currentUserId(authentication);
        if (callerId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login required");
        }
        if (resourceUserId == null || !callerId.equals(resourceUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot access another user's data");
        }
    }

    /**
     * Off by default: {@code X-Forwarded-For} is a plain request header, so
     * any client can set it to a fresh fake value on every request. Trusting
     * it unconditionally turns RateLimiterService into a no-op — a login
     * brute-forcer just sends a different X-Forwarded-For each attempt and
     * gets a brand-new bucket every time. Only flip this on
     * ({@code rewatch.security.trust-proxy-headers=true}) when this app
     * actually sits behind a reverse proxy/load balancer that overwrites
     * (not appends to) any client-supplied X-Forwarded-For before forwarding
     * — the current docker-compose setup exposes the backend directly, so
     * the header is fully attacker-controlled there.
     */
    private static volatile boolean trustProxyHeaders = false;

    static void setTrustProxyHeaders(boolean trust) {
        trustProxyHeaders = trust;
    }

    /** The caller's IP for rate-limiting. See {@link #trustProxyHeaders} for why X-Forwarded-For isn't trusted by default. */
    public static String clientIp(HttpServletRequest request) {
        if (trustProxyHeaders) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
