package com.rewatch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Small request bodies for the account-management endpoints, grouped in one file. */
public class AccountRequests {

    public static class ChangePassword {
        @NotNull private Long userId;
        @NotBlank private String currentPassword;
        @NotBlank private String newPassword;

        public Long getUserId() { return userId; }
        public void setUserId(Long v) { this.userId = v; }
        public String getCurrentPassword() { return currentPassword; }
        public void setCurrentPassword(String v) { this.currentPassword = v; }
        public String getNewPassword() { return newPassword; }
        public void setNewPassword(String v) { this.newPassword = v; }
    }

    public static class DeleteAccount {
        @NotNull private Long userId;
        @NotBlank private String password;

        public Long getUserId() { return userId; }
        public void setUserId(Long v) { this.userId = v; }
        public String getPassword() { return password; }
        public void setPassword(String v) { this.password = v; }
    }
}
