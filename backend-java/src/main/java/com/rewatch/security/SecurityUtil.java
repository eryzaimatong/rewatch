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

    /** X-Forwarded-For first — a real deployment sits behind nginx/a load balancer, where getRemoteAddr() would just be that proxy's own address. */
    public static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
