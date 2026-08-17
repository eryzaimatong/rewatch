package com.rewatch.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Entity
@Table(name = "users")
public class User {

    /**
     * READ_ONLY for the same mass-assignment reason as role/tokenVersion/etc
     * below, but the sharper case: AuthController.register binds a request
     * body straight onto this entity, and Spring Data's save() does a merge
     * (UPDATE) rather than an insert whenever id is non-null. Without this
     * guard, POSTing {"id": 1, "email": "...", ...} to /api/auth/register
     * would overwrite an arbitrary existing user's row — an unauthenticated
     * account takeover, not just a privilege escalation.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long id;

    /**
     * Bean Validation here (not just the JPA `nullable = false`) is what
     * turns a missing/blank email on POST /api/auth/register into a clean
     * 400 instead of an unhandled DataIntegrityViolationException surfacing
     * as a raw 500 with the underlying SQL error leaked to the client —
     * confirmed live: register() bound the request straight onto this
     * entity with no @Valid anywhere in the chain, so a request missing
     * email skipped every check and hit the NOT NULL constraint at the DB.
     * The frontend already always sends a fallback synthetic email (see
     * api.js's register()), so this only ever fires for a raw API caller
     * bypassing that — but it should fail cleanly regardless of who's calling.
     */
    @NotBlank
    @Email
    @Column(unique = true, nullable = false)
    private String email;

    /**
     * BCrypt hash, never the raw password. WRITE_ONLY (not @JsonIgnore, which would
     * also block deserializing the raw password out of the register request body)
     * is defense in depth on top of AuthController returning a dedicated response
     * DTO instead of the entity itself — this field must never leave the process
     * in a response body.
     */
    @Column(nullable = false)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    /**
     * No @Column(nullable = false) here deliberately — changing that now
     * would need a migration this app doesn't have (see DEPLOYMENT.md's
     * known gaps). @NotBlank still closes the actual gap: without it, a
     * request with a missing/blank username skipped straight past
     * register()'s own uniqueness check (`if (username != null) ...`,
     * a no-op for null) and created an account with no username at all —
     * unable to ever be found by findByUsername again, i.e. permanently
     * un-loggable-into.
     */
    @NotBlank
    private String username;

    /**
     * The trait vector seeded at onboarding, as ten comma-separated doubles.
     *
     * Replay starts here and then applies the rating log in order, so this must be
     * persisted rather than recomputed — otherwise a user's whole history would
     * shift every time onboarding logic changed.
     */
    @Column(name = "seed_vector", length = 500)
    private String seedVector;

    public enum Role { USER, ADMIN }

    /**
     * READ_ONLY (not WRITE_ONLY like password above): the inverse defense is
     * needed here. AuthController.register binds the request body straight onto
     * this entity, so without this annotation a client could POST
     * {"role":"ADMIN", ...} to /api/auth/register and self-grant admin. This
     * value must only ever be set server-side (see AuthController's admin-email
     * bootstrap), never accepted from a request body.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20) not null default 'USER'")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Role role = Role.USER;

    /**
     * Bumped on password change / reset — every previously-issued JWT embeds the
     * version it was issued under, and JwtAuthFilter rejects a token whose
     * embedded version doesn't match this column. This is the whole of this
     * app's session-revocation mechanism (there is no token blacklist/store).
     * Same mass-assignment risk as `role`, same fix.
     */
    @Column(name = "token_version", nullable = false, columnDefinition = "integer not null default 0")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private int tokenVersion = 0;

    /**
     * Defaults to public — matches the app's existing live behaviour (no
     * profile has ever been private) and preserves DNA-matching/discovery,
     * this app's core differentiator, for every existing account. A user
     * opts INTO privacy, rather than every account silently going dark the
     * moment this column is added. Same READ_ONLY mass-assignment guard as
     * `role`/`tokenVersion` above, changed only via AccountController.
     */
    @Column(name = "profile_public", nullable = false, columnDefinition = "boolean not null default true")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private boolean profilePublic = true;

    /**
     * Gates the one re-engagement email this app sends outside of password
     * reset (a new-follower notice, see NotificationService.notifyNewFollower)
     * — defaults to on so the feature actually does something for existing
     * accounts, with an explicit opt-out rather than requiring a user to find
     * and enable it. Same READ_ONLY mass-assignment guard as profilePublic —
     * changed only via AccountController.
     */
    @Column(name = "email_notifications_enabled", nullable = false, columnDefinition = "boolean not null default true")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private boolean emailNotificationsEnabled = true;

    /**
     * Comma-separated dealbreaker keys picked at onboarding (e.g.
     * "excessive gore,sad ending") — persisted so Recommender can keep
     * applying the hard filter on every later request, not just at
     * seed time. Same simple string-list style as seedVector.
     */
    @Column(length = 500)
    private String dealbreakers;

    /**
     * Personalizes interactive UI chrome (buttons, active chips, links, focus
     * rings) — one of "purple" (default, null reads as this), "blue",
     * "orange", "emerald". Deliberately does NOT recolor the brand mark or
     * TraitRadar's "your TasteDNA" line: that purple/cyan pairing is a
     * validated CVD-safe pair (see App.css's palette-discipline notes), and
     * the brand mark is the one fixed anchor everything else is allowed to
     * personalize around.
     */
    @Column(name = "accent_color", length = 20)
    private String accentColor;

    /**
     * A skin for this user's OWN /social/:id page only (what visitors see when
     * they land on it) — deliberately scoped there, not app-wide like
     * accentColor, so this doesn't fight the rest of the app's deliberately
     * restrained one-accent-per-screen palette. One of AccountService.
     * VALID_PROFILE_THEMES, null reads as "cinema" (the default look).
     */
    @Column(name = "profile_theme", length = 20)
    private String profileTheme;

    /**
     * A base64 `data:image/...` URL, client-resized/compressed before upload
     * (see Settings.jsx) — this app has no S3/CDN, so the image lives directly
     * in the row rather than as a stored-file reference. columnDefinition=TEXT
     * because a resized-but-still-real photo can run well past a VARCHAR's
     * practical length; AccountService caps the decoded size server-side.
     * READ_ONLY for the same reason as role/tokenVersion/profilePublic above —
     * AuthController.register binds a request body straight onto this entity,
     * so an unguarded field is settable from registration JSON, bypassing
     * AccountService's size/format validation entirely.
     */
    @Column(name = "avatar_url", columnDefinition = "TEXT")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String avatarUrl;

    /**
     * One of AccountService.FRAME_REQUIREMENTS's keys, or null for none.
     * READ_ONLY for the same mass-assignment reason as avatarUrl/role — only
     * settable via AccountService.setAvatarFrame, which re-checks eligibility
     * against AchievementService on every write rather than trusting a
     * previously-valid value (an achievement's underlying data, e.g. a
     * deleted rating, could in principle make a user newly ineligible).
     */
    @Column(name = "avatar_frame", length = 40)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String avatarFrame;

    /**
     * An expressive display line shown under the @username on the profile
     * header (e.g. "the girl who cries at movies") — purely decorative, never
     * used for lookup/auth/@mentions, so it carries none of username's
     * uniqueness or format constraints. Null renders nothing extra.
     * READ_ONLY for the same mass-assignment reason as avatarUrl/avatarFrame —
     * AccountService.setNickname is the only path that enforces the length cap.
     */
    @Column(length = 60)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String nickname;

    /** A short freeform "about me" line, shown on the profile below the nickname. */
    @Column(length = 200)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String bio;

    /**
     * Freeform "song of my current era" text (e.g. "Get Him Back! — Olivia
     * Rodrigo") — no music-service integration, no playback, just what the
     * user types. Not worth building a fake Spotify embed for.
     */
    @Column(name = "profile_song", length = 120)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String profileSong;

    /**
     * Up to 4 comma-separated Title ids the user chose to feature on their
     * profile — validated (see AccountService.setPinnedContent) against titles
     * they've actually rated, so "pinned" always means "something real I
     * watched," never an arbitrary catalog pick.
     */
    @Column(name = "pinned_title_ids", length = 60)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String pinnedTitleIds;

    /** Must be one of this user's own Rating rows with a real (non-blank) moment. */
    @Column(name = "pinned_rating_id")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long pinnedRatingId;

    /** Must be one of this user's own WatchlistFolder rows. */
    @Column(name = "pinned_folder_id")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long pinnedFolderId;

    public User() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getSeedVector() { return seedVector; }
    public void setSeedVector(String seedVector) { this.seedVector = seedVector; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public int getTokenVersion() { return tokenVersion; }
    public void setTokenVersion(int tokenVersion) { this.tokenVersion = tokenVersion; }

    public boolean isProfilePublic() { return profilePublic; }
    public void setProfilePublic(boolean v) { this.profilePublic = v; }

    public boolean isEmailNotificationsEnabled() { return emailNotificationsEnabled; }
    public void setEmailNotificationsEnabled(boolean v) { this.emailNotificationsEnabled = v; }

    public String getDealbreakers() { return dealbreakers; }
    public void setDealbreakers(String v) { this.dealbreakers = v; }

    public String getAccentColor() { return accentColor; }
    public void setAccentColor(String v) { this.accentColor = v; }

    public String getProfileTheme() { return profileTheme; }
    public void setProfileTheme(String v) { this.profileTheme = v; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String v) { this.avatarUrl = v; }

    public String getAvatarFrame() { return avatarFrame; }
    public void setAvatarFrame(String v) { this.avatarFrame = v; }

    public String getNickname() { return nickname; }
    public void setNickname(String v) { this.nickname = v; }

    public String getBio() { return bio; }
    public void setBio(String v) { this.bio = v; }

    public String getProfileSong() { return profileSong; }
    public void setProfileSong(String v) { this.profileSong = v; }

    public String getPinnedTitleIds() { return pinnedTitleIds; }
    public void setPinnedTitleIds(String v) { this.pinnedTitleIds = v; }

    public Long getPinnedRatingId() { return pinnedRatingId; }
    public void setPinnedRatingId(Long v) { this.pinnedRatingId = v; }

    public Long getPinnedFolderId() { return pinnedFolderId; }
    public void setPinnedFolderId(Long v) { this.pinnedFolderId = v; }
}