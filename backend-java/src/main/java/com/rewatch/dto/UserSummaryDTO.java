package com.rewatch.dto;

/** The lightweight shape used everywhere a user appears in a list: followers, following, DNA matches. */
public class UserSummaryDTO {

    private Long userId;
    private String username;
    private String archetype;
    private boolean isFollowing;

    public UserSummaryDTO() {}

    public UserSummaryDTO(Long userId, String username, String archetype, boolean isFollowing) {
        this.userId = userId;
        this.username = username;
        this.archetype = archetype;
        this.isFollowing = isFollowing;
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long v) { this.userId = v; }

    public String getUsername() { return username; }
    public void setUsername(String v) { this.username = v; }

    public String getArchetype() { return archetype; }
    public void setArchetype(String v) { this.archetype = v; }

    public boolean isFollowing() { return isFollowing; }
    public void setFollowing(boolean v) { this.isFollowing = v; }
}
