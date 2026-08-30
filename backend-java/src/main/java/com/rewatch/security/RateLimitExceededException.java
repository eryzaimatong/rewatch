package com.rewatch.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * A 429 that also carries how long until the caller has budget again — a
 * plain ResponseStatusException has nowhere to put that (SecurityExceptionHandler's
 * handler for it copies status + reason only, never headers), and this is
 * specifically for the routes that throw rather than build their own
 * ResponseEntity (TitleController, TmdbController's checkPublicRateLimit) —
 * changing every one of those routes' return type just to attach one header
 * would be a much bigger, riskier change than one subclass plus one
 * dedicated handler in SecurityExceptionHandler.
 */
public class RateLimitExceededException extends ResponseStatusException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(String reason, long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, reason);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
