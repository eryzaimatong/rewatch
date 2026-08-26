package com.rewatch.service.email;

/**
 * A send attempt's outcome as data, not a thrown exception — a delivery
 * failure is an expected, tracked business outcome here (it gets a FAILED
 * EmailDeliveryRecord row and an ERROR log), not exceptional Java control
 * flow. EmailSender implementations should only actually throw for a truly
 * unexpected bug (a null pointer, a malformed message), not for "the
 * provider was unreachable" or "the provider rejected it" — those come
 * back as a failure() result instead.
 */
public final class EmailSendResult {

    private final boolean success;
    private final String providerMessageId;
    private final String errorMessage;

    private EmailSendResult(boolean success, String providerMessageId, String errorMessage) {
        this.success = success;
        this.providerMessageId = providerMessageId;
        this.errorMessage = errorMessage;
    }

    public static EmailSendResult success(String providerMessageId) {
        return new EmailSendResult(true, providerMessageId, null);
    }

    public static EmailSendResult failure(String errorMessage) {
        return new EmailSendResult(false, null, errorMessage);
    }

    public boolean isSuccess() { return success; }
    public String getProviderMessageId() { return providerMessageId; }
    public String getErrorMessage() { return errorMessage; }
}
