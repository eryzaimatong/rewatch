package com.rewatch.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rewatch.model.EmailDeliveryRecord;
import com.rewatch.repository.EmailDeliveryRecordRepository;
import com.rewatch.service.email.EmailSendResult;
import com.rewatch.service.email.EmailSender;

/**
 * The actual assertion this session's email bug needed and never had:
 * every send attempt produces a delivery record that reflects what really
 * happened, and a failure is never allowed to become a thrown exception
 * that would break the caller's "always succeeds" contract — regardless of
 * whether the sender returned a clean failure() or threw outright.
 */
@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock private EmailSender emailSender;
    @Mock private EmailDeliveryRecordRepository deliveryRepo;

    private EmailService newService() {
        return new EmailService(emailSender, deliveryRepo);
    }

    @Test
    void successfulSendCreatesAPendingRecordThenMarksItSent() {
        when(emailSender.send(any())).thenReturn(EmailSendResult.success("provider-msg-1"));

        newService().sendPasswordResetEmail("user@example.com", "eryza", "https://app/reset?token=x");

        // EmailDeliveryRecord is mutable and every save() call after the
        // first mutates the SAME instance an ArgumentCaptor would have
        // already captured — asserting on a captured "snapshot" would
        // really just be asserting on the final state twice. What actually
        // matters and IS verifiable: a row gets persisted separately at
        // PENDING time (durability — a row exists even if the send
        // attempt never completes) before being updated to its final
        // state, proven by the save() call count; the final state itself
        // is checked directly on the record.
        ArgumentCaptor<EmailDeliveryRecord> captor = ArgumentCaptor.forClass(EmailDeliveryRecord.class);
        verify(deliveryRepo, times(2)).save(captor.capture());

        EmailDeliveryRecord record = captor.getValue();
        assertEquals(EmailDeliveryRecord.Type.PASSWORD_RESET, record.getType());
        assertEquals(EmailDeliveryRecord.Status.SENT, record.getStatus());
        assertEquals("provider-msg-1", record.getProviderMessageId());
    }

    @Test
    void aFailedSendResultMarksTheRecordFailedButDoesNotThrow() {
        when(emailSender.send(any())).thenReturn(EmailSendResult.failure("Connect timed out"));

        assertDoesNotThrow(() ->
                newService().sendNewFollowerEmail("user@example.com", "eryza", "afan", "https://app"));

        ArgumentCaptor<EmailDeliveryRecord> captor = ArgumentCaptor.forClass(EmailDeliveryRecord.class);
        verify(deliveryRepo, times(2)).save(captor.capture());
        EmailDeliveryRecord failed = captor.getValue();
        assertEquals(EmailDeliveryRecord.Status.FAILED, failed.getStatus());
        assertEquals("Connect timed out", failed.getErrorMessage());
    }

    @Test
    void anUnexpectedExceptionFromTheSenderStillMarksFailedAndDoesNotThrow() {
        when(emailSender.send(any())).thenThrow(new RuntimeException("boom"));

        assertDoesNotThrow(() ->
                newService().sendPasswordResetEmail("user@example.com", "eryza", "https://app/reset?token=x"));

        ArgumentCaptor<EmailDeliveryRecord> captor = ArgumentCaptor.forClass(EmailDeliveryRecord.class);
        verify(deliveryRepo, times(2)).save(captor.capture());
        assertEquals(EmailDeliveryRecord.Status.FAILED, captor.getValue().getStatus());
    }

    @Test
    void everySendGetsItsOwnCorrelationId() {
        when(emailSender.send(any())).thenReturn(EmailSendResult.success("id"));

        newService().sendPasswordResetEmail("a@example.com", "a", "link1");
        newService().sendPasswordResetEmail("b@example.com", "b", "link2");

        ArgumentCaptor<EmailDeliveryRecord> captor = ArgumentCaptor.forClass(EmailDeliveryRecord.class);
        verify(deliveryRepo, times(4)).save(captor.capture());
        String firstCorrelationId = captor.getAllValues().get(0).getCorrelationId();
        String secondCorrelationId = captor.getAllValues().get(2).getCorrelationId();
        org.junit.jupiter.api.Assertions.assertNotEquals(firstCorrelationId, secondCorrelationId);
    }
}
