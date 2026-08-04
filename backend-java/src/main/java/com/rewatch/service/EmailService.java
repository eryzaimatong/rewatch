package com.rewatch.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Thin wrapper around JavaMailSender. Plain text, not templated — there's
 * exactly one email in this app so far, and pulling in a templating engine
 * for it isn't worth the dependency yet.
 */
@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public EmailService(JavaMailSender mailSender, @Value("${rewatch.mail.from}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    public void sendPasswordResetEmail(String to, String username, String resetLink) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject("Reset your Re:Watch password");
        message.setText(
                "Hi " + username + ",\n\n"
                + "Someone (hopefully you) asked to reset your Re:Watch password. "
                + "This link expires in 45 minutes:\n\n"
                + resetLink + "\n\n"
                + "If you didn't request this, you can safely ignore this email — "
                + "your password won't change unless you click the link above.");
        mailSender.send(message);
    }
}
