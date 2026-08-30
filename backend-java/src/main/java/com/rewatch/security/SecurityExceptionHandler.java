package com.rewatch.security;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * SecurityUtil.requireSelf throws ResponseStatusException to signal 401/403.
 * Left to Spring's default resolution, that exception can propagate out of
 * DispatcherServlet and into Spring Security's ExceptionTranslationFilter, which
 * treats a 403 for an unauthenticated/anonymous SecurityContext as "start
 * authentication" and routes it through the configured AuthenticationEntryPoint
 * — silently turning a "you are logged in as the wrong user" 403 into a "please
 * log in" 401. Handling it explicitly here, at the highest-precedence resolver
 * (@RestControllerAdvice / ExceptionHandlerExceptionResolver), fully commits the
 * response inside the servlet dispatch so it never reaches that filter.
 *
 * The catch-all below exists because that same misrouting isn't specific to
 * ResponseStatusException: a real bug once surfaced here as a plain
 * TransactionRequiredException (a service method missing @Transactional), and
 * left unhandled it went through the *same* ExceptionTranslationFilter path and
 * came back to the client as a confusing 401 "Login required" instead of a 500
 * — actively hiding the real error. Handling every exception at this
 * highest-precedence resolver keeps whatever status/message is appropriate
 * from ever being reinterpreted as an auth failure downstream.
 */
@RestControllerAdvice
public class SecurityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(SecurityExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> handle(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("status", "error", "message",
                        ex.getReason() != null ? ex.getReason() : "Request failed"));
    }

    /**
     * More specific than the plain ResponseStatusException handler above —
     * Spring resolves @ExceptionHandler by nearest matching type, so this
     * one wins for RateLimitExceededException specifically, and is the only
     * place the Retry-After header (and the machine-readable
     * retryAfterSeconds a client could use instead of parsing the message)
     * actually gets attached to the response.
     */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<?> handleRateLimit(RateLimitExceededException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(Map.of(
                        "status", "error",
                        "message", ex.getReason() != null ? ex.getReason() : "Too many requests.",
                        "retryAfterSeconds", ex.getRetryAfterSeconds()));
    }

    /**
     * Without this, a @Valid failure (e.g. POST /api/auth/register missing
     * email) fell through to the catch-all below — the same "no explicit
     * handler for this exception type" gap that used to misroute 403s as
     * 401s, here turning a real, clean 400 into a generic 500. Same
     * highest-precedence-resolver reasoning as the class-level doc comment.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .orElse("Invalid request.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("status", "error", "message", message));
    }

    /**
     * The one gap that actually matters most in production: without this log
     * line, an unexpected exception here was returned to the client as a
     * clean, generic 500 and then vanished — nothing written anywhere, so a
     * real failure in prod was invisible even to someone actively checking
     * `render logs`. Logging the full exception plus the method+URI that
     * triggered it turns those logs into the only error-monitoring this app
     * has, which is meaningfully better than none.
     *
     * Every endpoint that doesn't catch its own exceptions (search,
     * nlp-search, understand among them — none of the three had a try/catch
     * of their own) lands here, so this is the one place a correlationId
     * needed to be attached to cover all of them at once, the same shape
     * onboard/refine's own try/catch already used — a report of "search
     * failed" is otherwise unmatchable to a specific log line.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleUnexpected(Exception ex, HttpServletRequest request) {
        String correlationId = UUID.randomUUID().toString();
        log.error("[correlationId={}] Unhandled exception on {} {}",
                correlationId, request.getMethod(), request.getRequestURI(), ex);
        Map<String, Object> body = new HashMap<>();
        body.put("status", "error");
        body.put("message", "Unexpected server error");
        body.put("correlationId", correlationId);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
