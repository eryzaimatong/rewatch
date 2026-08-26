package com.rewatch.service.email;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

/**
 * Resend (https://resend.com/docs/api-reference/emails/send-email) via plain
 * HTTPS POST — chosen specifically because outbound SMTP is blocked on this
 * app's current host (confirmed live: both port 587 and 465 hit
 * SocketTimeoutException) while every other outbound HTTPS call this app
 * makes, TMDB included, works fine. Resend's own Idempotency-Key header
 * support is used directly rather than building a dedup layer here — the
 * correlationId IS the idempotency key, so a retried request (from the
 * bounded retry below, or a caller-level retry) can never create two
 * separate emails for the same logical send.
 *
 * UNVERIFIED against a live account: this was built and unit-tested against
 * a mocked RestTemplate, but no real RESEND_API_KEY has ever been issued to
 * this app, so the actual request/response shape has not been confirmed
 * against Resend's real API. Do not treat "reads correctly against the
 * documented API shape" as equivalent to "confirmed working."
 */
public class HttpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(HttpEmailSender.class);

    private static final int MAX_ATTEMPTS = 3;
    private static final long[] BACKOFF_MS = {300, 900};

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String fromAddress;
    private final String apiUrl;

    public HttpEmailSender(RestTemplate restTemplate, String apiKey, String fromAddress, String apiUrl) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
        this.fromAddress = fromAddress;
        this.apiUrl = apiUrl;
    }

    @Override
    public EmailSendResult send(EmailMessage message) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        // Resend's own idempotency support: a retried attempt below (or a
        // caller that somehow calls send() twice for the same logical
        // request) can never result in two emails actually going out.
        headers.set("Idempotency-Key", message.correlationId());

        Map<String, Object> body = Map.of(
                "from", fromAddress,
                "to", List.of(message.to()),
                "subject", message.subject(),
                "text", message.body()
        );
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        String lastError = "Unknown error";
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> response = restTemplate.postForObject(apiUrl, request, Map.class);
                Object id = response == null ? null : response.get("id");
                return EmailSendResult.success(id == null ? null : id.toString());
            } catch (HttpClientErrorException e) {
                // 4xx — a bad request or bad API key. Retrying an identical
                // request won't change the outcome; only wastes the bounded
                // attempt budget on a call that's going to fail every time.
                log.error("[correlationId={}] Resend rejected the request (attempt {}/{}): {} {}",
                        message.correlationId(), attempt, MAX_ATTEMPTS, e.getStatusCode(), e.getResponseBodyAsString());
                return EmailSendResult.failure("Resend " + e.getStatusCode() + ": " + e.getResponseBodyAsString());
            } catch (HttpServerErrorException | ResourceAccessException e) {
                // 5xx or a connect/read timeout / network failure — the only
                // cases actually worth retrying, since they're plausibly
                // transient rather than a request that's wrong on its face.
                lastError = e.getMessage();
                log.warn("[correlationId={}] Resend send attempt {}/{} failed, {}: {}",
                        message.correlationId(), attempt, MAX_ATTEMPTS,
                        attempt < MAX_ATTEMPTS ? "retrying" : "giving up", lastError);
                if (attempt < MAX_ATTEMPTS) {
                    sleep(BACKOFF_MS[attempt - 1]);
                }
            }
        }

        log.error("[correlationId={}] Resend send exhausted all {} attempts: {}",
                message.correlationId(), MAX_ATTEMPTS, lastError);
        return EmailSendResult.failure(lastError);
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
