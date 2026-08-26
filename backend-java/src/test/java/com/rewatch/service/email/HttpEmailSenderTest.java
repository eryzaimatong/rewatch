package com.rewatch.service.email;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

/**
 * UNVERIFIED against a real Resend account — no RESEND_API_KEY has ever
 * been issued to this app. These assert HttpEmailSender's own logic
 * (retry/backoff bounds, which failures are retried vs not, the
 * idempotency header) against a mocked RestTemplate, which is everything
 * that's actually testable without a live account.
 */
@ExtendWith(MockitoExtension.class)
class HttpEmailSenderTest {

    @Mock private RestTemplate restTemplate;

    private HttpEmailSender newSender() {
        return new HttpEmailSender(restTemplate, "test-key", "noreply@rewatch.local", "https://api.resend.com/emails");
    }

    private EmailMessage message() {
        return new EmailMessage("user@example.com", "Subject", "Body", "corr-123");
    }

    @Test
    void successfulSendReturnsTheProviderMessageId() {
        when(restTemplate.postForObject(any(String.class), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(Map.of("id", "resend-msg-abc"));

        EmailSendResult result = newSender().send(message());

        assertTrue(result.isSuccess());
        assertEquals("resend-msg-abc", result.getProviderMessageId());
    }

    @Test
    void setsTheIdempotencyKeyToTheCorrelationId() {
        when(restTemplate.postForObject(any(String.class), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(Map.of("id", "x"));

        newSender().send(message());

        org.mockito.ArgumentCaptor<HttpEntity> captor = org.mockito.ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForObject(any(String.class), captor.capture(), eq(Map.class));
        HttpHeaders headers = captor.getValue().getHeaders();
        assertEquals("corr-123", headers.getFirst("Idempotency-Key"));
    }

    @Test
    void a4xxFailsImmediatelyWithoutRetrying() {
        when(restTemplate.postForObject(any(String.class), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request", null, null, null));

        EmailSendResult result = newSender().send(message());

        assertFalse(result.isSuccess());
        verify(restTemplate, times(1)).postForObject(any(String.class), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    void a5xxRetriesUpToTheBoundThenFails() {
        when(restTemplate.postForObject(any(String.class), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "Server Error", null, null, null));

        EmailSendResult result = newSender().send(message());

        assertFalse(result.isSuccess());
        // MAX_ATTEMPTS = 3 — verifies the retry loop is actually bounded, not unbounded.
        verify(restTemplate, times(3)).postForObject(any(String.class), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    void aConnectTimeoutIsTreatedAsRetriableAndEventuallyFails() {
        when(restTemplate.postForObject(any(String.class), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new ResourceAccessException("Connect timed out"));

        EmailSendResult result = newSender().send(message());

        assertFalse(result.isSuccess());
        verify(restTemplate, times(3)).postForObject(any(String.class), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    void succeedsOnASecondAttemptAfterATransientFailure() {
        when(restTemplate.postForObject(any(String.class), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new ResourceAccessException("timeout"))
                .thenReturn(Map.of("id", "resend-msg-retry"));

        EmailSendResult result = newSender().send(message());

        assertTrue(result.isSuccess());
        assertEquals("resend-msg-retry", result.getProviderMessageId());
        verify(restTemplate, times(2)).postForObject(any(String.class), any(HttpEntity.class), eq(Map.class));
    }
}
