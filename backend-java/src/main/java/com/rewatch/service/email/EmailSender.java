package com.rewatch.service.email;

/**
 * The seam that keeps the actual provider (SMTP today, an HTTP transactional
 * API tomorrow) out of PasswordResetService/NotificationService entirely —
 * neither of those knows or should know which EmailSender bean is active.
 * Selected by rewatch.mail.provider (see EmailConfig): "smtp" or "http".
 *
 * Implementations own their own retry/backoff and timeout policy internally
 * — send() should not throw for an ordinary delivery failure (unreachable
 * provider, rejected message); it returns EmailSendResult.failure(...) for
 * that. It should only throw for a genuine bug in the implementation itself.
 */
public interface EmailSender {
    EmailSendResult send(EmailMessage message);
}
