package com.rewatch.dto;

/** The lightweight shape used everywhere a user appears in a list: followers, following, DNA matches. */
public class UserSummaryDTO {

    private Long userId;
    private String username;
    private String archetype;
    private boolean isFollowing;

    /** Only populated by dnaMatches() — 0 elsewhere (followers/following lists don't rank by it). */
    private double matchScore;

    public UserSummaryDTO() {}

    public UserSummaryDTO(Long userId, String username, String archetype, boolean isFollowing) {
        this(userId, username, archetype, isFollowing, 0.0);
    }

    public UserSummaryDTO(Long userId, String username, String archetype, boolean isFollowing, double matchScore) {
        this.userId = userId;
        this.username = username;
        this.archetype = archetype;
        this.isFollowing = isFollowing;
        this.matchScore = matchScore;
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long v) { this.userId = v; }

    public String getUsername() { return username; }
    public void setUsername(String v) { this.username = v; }

    public String getArchetype() { return archetype; }
    public void setArchetype(String v) { this.archetype = v; }

    public boolean isFollowing() { return isFollowing; }
    public void setFollowing(boolean v) { this.isFollowing = v; }

    public double getMatchScore() { return matchScore; }
    public void setMatchScore(double v) { this.matchScore = v; }
}
