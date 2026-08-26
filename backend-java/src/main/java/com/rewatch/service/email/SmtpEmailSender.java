package com.rewatch.service.email;

import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * The original transport — kept, not deleted, because SMTP is genuinely the
 * right choice on infrastructure that doesn't block it (this app's current
 * host does; see application.properties' spring.mail.* comment). Connect/
 * read/write timeouts live in spring.mail.properties.mail.smtp.* (the fix
 * that turned a request-hanging bug into a fast, observable failure) rather
 * than here, so they're one property change away from the JavaMailSender
 * bean Spring already wires up from application.properties — no reason to
 * duplicate that config in code.
 *
 * SMTP has no concept of a caller-supplied idempotency key or a returned
 * provider message id the way an HTTP API does — mailSender.send() either
 * completes or throws, with no server-assigned identifier to report back.
 */
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public SmtpEmailSender(JavaMailSender mailSender, String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public EmailSendResult send(EmailMessage message) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(fromAddress);
        mail.setTo(message.to());
        mail.setSubject(message.subject());
        mail.setText(message.body());
        try {
            mailSender.send(mail);
            return EmailSendResult.success(null);
        } catch (MailException e) {
            return EmailSendResult.failure(e.getMessage());
        }
    }
}
