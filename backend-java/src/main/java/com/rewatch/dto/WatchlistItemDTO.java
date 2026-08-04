package com.rewatch.dto;

import java.time.Instant;

public class WatchlistItemDTO {

    private Long id;
    private Long titleId;
    private Integer tmdbId;
    private String title;
    private String poster;
    private String synopsis;
    private int year;
    private double matchScore;
    private Long folderId;
    private String folderName;
    private Instant addedAt;

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }

    public Long getTitleId() { return titleId; }
    public void setTitleId(Long v) { this.titleId = v; }

    public Integer getTmdbId() { return tmdbId; }
    public void setTmdbId(Integer v) { this.tmdbId = v; }

    public String getTitle() { return title; }
    public void setTitle(String v) { this.title = v; }

    public String getPoster() { return poster; }
    public void setPoster(String v) { this.poster = v; }

    public String getSynopsis() { return synopsis; }
    public void setSynopsis(String v) { this.synopsis = v; }

    public int getYear() { return year; }
    public void setYear(int v) { this.year = v; }

    public double getMatchScore() { return matchScore; }
    public void setMatchScore(double v) { this.matchScore = v; }

    public Long getFolderId() { return folderId; }
    public void setFolderId(Long v) { this.folderId = v; }

    public String getFolderName() { return folderName; }
    public void setFolderName(String v) { this.folderName = v; }

    public Instant getAddedAt() { return addedAt; }
    public void setAddedAt(Instant v) { this.addedAt = v; }
}
