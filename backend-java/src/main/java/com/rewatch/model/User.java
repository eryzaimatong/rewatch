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

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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
}