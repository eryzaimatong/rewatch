package com.rewatch.service;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.rewatch.model.EmailDeliveryRecord;
import com.rewatch.repository.EmailDeliveryRecordRepository;
import com.rewatch.service.email.EmailMessage;
import com.rewatch.service.email.EmailSendResult;
import com.rewatch.service.email.EmailSender;

/**
 * The actual fix for the email bug surviving unnoticed: every send attempt
 * now gets an EmailDeliveryRecord (PENDING -> SENT/FAILED) plus an ERROR log
 * carrying the correlation id on failure — regardless of which EmailSender
 * (SMTP or HTTP) is behind the call. "The user shouldn't see a send
 * failure" was never a reason for no one to see it: PasswordResetService
 * and NotificationService still get the same silent-to-the-caller contract
 * they always had (this never throws for an ordinary delivery failure —
 * that's tracked as data now, not a Java exception), but a broken mail path
 * is now a query away instead of something nobody notices until a user
 * complains their reset link never arrived.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final EmailSender emailSender;
    private final EmailDeliveryRecordRepository deliveryRepo;

    public EmailService(EmailSender emailSender, EmailDeliveryRecordRepository deliveryRepo) {
        this.emailSender = emailSender;
        this.deliveryRepo = deliveryRepo;
    }

    public void sendPasswordResetEmail(String to, String username, String resetLink) {
        String body = "Hi " + username + ",\n\n"
                + "Someone (hopefully you) asked to reset your Re:Watch password. "
                + "This link expires in 45 minutes:\n\n"
                + resetLink + "\n\n"
                + "If you didn't request this, you can safely ignore this email — "
                + "your password won't change unless you click the link above.";
        send(to, "Reset your Re:Watch password", body, EmailDeliveryRecord.Type.PASSWORD_RESET);
    }

    /**
     * See NotificationService.notifyNewFollower — this app's only
     * re-engagement email, deliberately not sent for every notification type.
     */
    public void sendNewFollowerEmail(String to, String username, String followerUsername, String appUrl) {
        String body = "Hi " + username + ",\n\n"
                + followerUsername + " just started following you on Re:Watch.\n\n"
                + "See their profile and yours: " + appUrl + "\n\n"
                + "You're getting this because email notifications are on for your account — "
                + "turn them off any time in Settings.";
        send(to, followerUsername + " started following you on Re:Watch", body, EmailDeliveryRecord.Type.NEW_FOLLOWER);
    }

    private void send(String to, String subject, String body, EmailDeliveryRecord.Type type) {
        String correlationId = UUID.randomUUID().toString();
        EmailDeliveryRecord record = new EmailDeliveryRecord(correlationId, to, type);
        deliveryRepo.save(record);

        EmailMessage message = new EmailMessage(to, subject, body, correlationId);
        EmailSendResult result;
        try {
            result = emailSender.send(message);
        } catch (RuntimeException e) {
            // A genuine bug in the EmailSender implementation itself (not an
            // ordinary delivery failure — those come back as
            // EmailSendResult.failure() without throwing). Still recorded and
            // logged the same way, not rethrown: this method's contract is
            // "never breaks the caller's request" regardless of why sending
            // failed.
            record.markFailed(e.getMessage());
            deliveryRepo.save(record);
            log.error("[correlationId={}] Unexpected exception sending {} email to {}",
                    correlationId, type, to, e);
            return;
        }

        if (result.isSuccess()) {
            record.markSent(result.getProviderMessageId());
            deliveryRepo.save(record);
        } else {
            record.markFailed(result.getErrorMessage());
            deliveryRepo.save(record);
            log.error("[correlationId={}] Failed to send {} email to {}: {}",
                    correlationId, type, to, result.getErrorMessage());
        }
    }
}
