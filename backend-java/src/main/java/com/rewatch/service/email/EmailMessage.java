package com.rewatch.service.email;

/**
 * Transport-agnostic — SmtpEmailSender and HttpEmailSender both take this
 * same shape, which is the whole point: EmailService builds one of these
 * without knowing or caring which implementation is behind the configured
 * EmailSender bean.
 */
public record EmailMessage(String to, String subject, String body, String correlationId) {
}
