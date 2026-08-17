package com.rewatch.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Thin wrapper around JavaMailSender. Plain text, not templated — there are
 * only two emails in this app so far, and pulling in a templating engine
 * for them isn't worth the dependency yet.
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

    /**
     * This app's only re-engagement email — see NotificationService.notifyNewFollower.
     * Deliberately not sent for every notification type (likes, comments,
     * milestones): those are frequent enough that emailing on each would be
     * spammy and would burn through a single Gmail account's daily send limit
     * fast. A new follower is rare and meaningful enough to be worth a
     * "someone thinks about you" nudge to come back.
     */
    public void sendNewFollowerEmail(String to, String username, String followerUsername, String appUrl) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject(followerUsername + " started following you on Re:Watch");
        message.setText(
                "Hi " + username + ",\n\n"
                + followerUsername + " just started following you on Re:Watch.\n\n"
                + "See their profile and yours: " + appUrl + "\n\n"
                + "You're getting this because email notifications are on for your account — "
                + "turn them off any time in Settings.");
        mailSender.send(message);
    }
}
