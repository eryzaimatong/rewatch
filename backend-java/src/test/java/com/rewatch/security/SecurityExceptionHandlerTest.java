package com.rewatch.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Regression test for the fixed catch-all handler: an unexpected exception
 * used to return a clean 500 to the client while being logged nowhere at
 * all, making a real production failure invisible even to someone actively
 * checking the logs. This locks in that the handler still resolves the
 * request's method + URI (the context that makes the log line actually
 * useful) without throwing a secondary error itself.
 */
@ExtendWith(MockitoExtension.class)
class SecurityExceptionHandlerTest {

    @Mock private HttpServletRequest request;

    @Test
    void unexpectedExceptionReturns500WithoutThrowing() {
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/ratings");

        ResponseEntity<?> response = new SecurityExceptionHandler()
                .handleUnexpected(new RuntimeException("boom"), request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }
}
