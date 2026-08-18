package com.rewatch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Small request bodies for the watchlist endpoints, grouped in one file. */
public class WatchlistRequests {

    public static class CreateFolder {
        @NotNull private Long userId;
        // Matches WatchlistFolder.name's @Column(length = 60) — without this,
        // an over-length name passed @NotBlank fine and only failed once it hit
        // the database's own length constraint, surfacing as a raw
        // DataIntegrityViolationException the generic handler turns into an
        // opaque 500 with no message the frontend could show.
        @NotBlank @Size(max = 60, message = "must be 60 characters or fewer") private String name;

        public Long getUserId() { return userId; }
        public void setUserId(Long v) { this.userId = v; }
        public String getName() { return name; }
        public void setName(String v) { this.name = v; }
    }

    public static class RenameFolder {
        @NotNull private Long userId;
        @NotBlank @Size(max = 60, message = "must be 60 characters or fewer") private String name;

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

    public static class SetFolderCollaborative {
        @NotNull private Long userId;
        @NotNull private Boolean collaborative;

        public Long getUserId() { return userId; }
        public void setUserId(Long v) { this.userId = v; }
        public Boolean getCollaborative() { return collaborative; }
        public void setCollaborative(Boolean v) { this.collaborative = v; }
    }
}
