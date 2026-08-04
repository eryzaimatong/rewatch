package com.rewatch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Small request bodies for the watchlist endpoints, grouped in one file. */
public class WatchlistRequests {

    public static class CreateFolder {
        @NotNull private Long userId;
        @NotBlank private String name;

        public Long getUserId() { return userId; }
        public void setUserId(Long v) { this.userId = v; }
        public String getName() { return name; }
        public void setName(String v) { this.name = v; }
    }

    public static class AddItem {
        @NotNull private Long userId;
        private Integer tmdbId;
        private Long titleId;
        private String title;
        private Long folderId;

        public Long getUserId() { return userId; }
        public void setUserId(Long v) { this.userId = v; }
        public Integer getTmdbId() { return tmdbId; }
        public void setTmdbId(Integer v) { this.tmdbId = v; }
        public Long getTitleId() { return titleId; }
        public void setTitleId(Long v) { this.titleId = v; }
        public String getTitle() { return title; }
        public void setTitle(String v) { this.title = v; }
        public Long getFolderId() { return folderId; }
        public void setFolderId(Long v) { this.folderId = v; }
    }

    public static class MoveItem {
        @NotNull private Long userId;
        private Long folderId;

        public Long getUserId() { return userId; }
        public void setUserId(Long v) { this.userId = v; }
        public Long getFolderId() { return folderId; }
        public void setFolderId(Long v) { this.folderId = v; }
    }

    public static class SetFolderVisibility {
        @NotNull private Long userId;
        @NotNull private Boolean isPublic;

        public Long getUserId() { return userId; }
        public void setUserId(Long v) { this.userId = v; }
        public Boolean getIsPublic() { return isPublic; }
        public void setIsPublic(Boolean v) { this.isPublic = v; }
    }
}
