package com.rewatch.dto;

import java.util.List;

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

    public static class SetProfileVisibility {
        @NotNull private Long userId;
        @NotNull private Boolean isPublic;

        public Long getUserId() { return userId; }
        public void setUserId(Long v) { this.userId = v; }
        public Boolean getIsPublic() { return isPublic; }
        public void setIsPublic(Boolean v) { this.isPublic = v; }
    }

    public static class SetAccentColor {
        @NotNull private Long userId;
        @NotBlank private String accentColor;

        public Long getUserId() { return userId; }
        public void setUserId(Long v) { this.userId = v; }
        public String getAccentColor() { return accentColor; }
        public void setAccentColor(String v) { this.accentColor = v; }
    }

    public static class SetProfileTheme {
        @NotNull private Long userId;
        @NotBlank private String profileTheme;

        public Long getUserId() { return userId; }
        public void setUserId(Long v) { this.userId = v; }
        public String getProfileTheme() { return profileTheme; }
        public void setProfileTheme(String v) { this.profileTheme = v; }
    }

    public static class SetAvatar {
        @NotNull private Long userId;
        /** A data:image/... URL, or blank/null to remove the current avatar. */
        private String avatarUrl;

        public Long getUserId() { return userId; }
        public void setUserId(Long v) { this.userId = v; }
        public String getAvatarUrl() { return avatarUrl; }
        public void setAvatarUrl(String v) { this.avatarUrl = v; }
    }

    public static class SetAvatarFrame {
        @NotNull private Long userId;
        /** One of AccountService.FRAME_REQUIREMENTS's keys, or blank/null to remove the current frame. */
        private String frame;

        public Long getUserId() { return userId; }
        public void setUserId(Long v) { this.userId = v; }
        public String getFrame() { return frame; }
        public void setFrame(String v) { this.frame = v; }
    }

    public static class SetNickname {
        @NotNull private Long userId;
        /** Blank/null clears the current nickname. */
        private String nickname;

        public Long getUserId() { return userId; }
        public void setUserId(Long v) { this.userId = v; }
        public String getNickname() { return nickname; }
        public void setNickname(String v) { this.nickname = v; }
    }

    public static class SetBio {
        @NotNull private Long userId;
        /** Blank/null clears the current bio. */
        private String bio;

        public Long getUserId() { return userId; }
        public void setUserId(Long v) { this.userId = v; }
        public String getBio() { return bio; }
        public void setBio(String v) { this.bio = v; }
    }

    public static class SetProfileSong {
        @NotNull private Long userId;
        /** Blank/null clears the current profile song. */
        private String profileSong;

        public Long getUserId() { return userId; }
        public void setUserId(Long v) { this.userId = v; }
        public String getProfileSong() { return profileSong; }
        public void setProfileSong(String v) { this.profileSong = v; }
    }

    public static class SetPinnedContent {
        @NotNull private Long userId;
        /** Up to 4 title ids; null/empty unpins. */
        private List<Long> titleIds;
        /** null unpins. */
        private Long ratingId;
        /** null unpins. */
        private Long folderId;

        public Long getUserId() { return userId; }
        public void setUserId(Long v) { this.userId = v; }
        public List<Long> getTitleIds() { return titleIds; }
        public void setTitleIds(List<Long> v) { this.titleIds = v; }
        public Long getRatingId() { return ratingId; }
        public void setRatingId(Long v) { this.ratingId = v; }
        public Long getFolderId() { return folderId; }
        public void setFolderId(Long v) { this.folderId = v; }
    }
}
