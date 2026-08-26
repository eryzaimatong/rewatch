package com.rewatch.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.client.RestTemplate;

import com.rewatch.service.email.EmailSender;
import com.rewatch.service.email.HttpEmailSender;
import com.rewatch.service.email.SmtpEmailSender;

/**
 * Selects the active EmailSender by rewatch.mail.provider ("smtp", the
 * default, or "http") — PasswordResetService/NotificationService depend on
 * the EmailSender interface only, never on either implementation directly.
 *
 * Fail-fast is @ConditionalOnProperty-scoped rather than a blanket
 * @PostConstruct check: httpEmailSender() below requires resend.api-key
 * with NO fallback default, but that @Value is only ever resolved when this
 * bean is actually being constructed — i.e. only when
 * rewatch.mail.provider=http is set. Running in smtp mode (the default)
 * never touches that property at all, so it can't fail boot over config
 * that mode doesn't need. Switching to http mode with RESEND_API_KEY unset
 * fails Spring's own property-placeholder resolution at context-startup,
 * the same "refuse to start rather than start broken" pattern
 * application-prod.properties already uses for JWT_SECRET/MAIL_USERNAME.
 *
 * One honest wrinkle: Spring Boot's own MailSenderAutoConfiguration builds a
 * JavaMailSender bean automatically whenever spring.mail.host is set (it
 * is, unconditionally, in application.properties) — independent of which
 * EmailSender this class selects. That means MAIL_USERNAME/MAIL_PASSWORD
 * stay required in prod (application-prod.properties has no fallback for
 * them) even when running in http mode, where they're functionally unused.
 * Not a functional bug, just a config requirement that outlives its own
 * purpose — noted rather than silently surprising whoever sets this up.
 */
@Configuration
public class EmailConfig {

    @Value("${resend.connect-timeout-ms:5000}")
    private long connectTimeoutMs;

    @Value("${resend.read-timeout-ms:8000}")
    private long readTimeoutMs;

    @Bean
    public RestTemplate resendRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .setReadTimeout(Duration.ofMillis(readTimeoutMs))
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "rewatch.mail.provider", havingValue = "smtp", matchIfMissing = true)
    public EmailSender smtpEmailSender(JavaMailSender mailSender,
                                        @Value("${rewatch.mail.from}") String fromAddress) {
        return new SmtpEmailSender(mailSender, fromAddress);
    }

    @Bean
    @ConditionalOnProperty(name = "rewatch.mail.provider", havingValue = "http")
    public EmailSender httpEmailSender(RestTemplate resendRestTemplate,
                                        @Value("${resend.api-key}") String apiKey,
                                        @Value("${rewatch.mail.from}") String fromAddress,
                                        @Value("${resend.api-url:https://api.resend.com/emails}") String apiUrl) {
        return new HttpEmailSender(resendRestTemplate, apiKey, fromAddress, apiUrl);
    }
}
