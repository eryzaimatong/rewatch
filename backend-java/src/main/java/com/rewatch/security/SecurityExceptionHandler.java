package com.rewatch.security;

import java.util.Map;

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

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> handle(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("status", "error", "message",
                        ex.getReason() != null ? ex.getReason() : "Request failed"));
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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleUnexpected(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("status", "error", "message", "Unexpected server error"));
    }
}
