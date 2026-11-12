package com.rewatch.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One friend's answers inside a {@link MovieNight}. */
@Entity
@Table(name = "movie_night_members")
public class MovieNightMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "night_id", nullable = false)
    private Long nightId;

    @Column(nullable = false, length = 24)
    private String name;

    /** Comma-separated answer indexes, e.g. "2,0,1,1,0". */
    @Column(nullable = false, length = 32)
    private String answers;

    /** Comma-separated trait values in {@link Trait} ordinal order. */
    @Column(nullable = false, length = 160)
    private String vector;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    protected MovieNightMember() { }

    public MovieNightMember(Long nightId, String name, String answers, String vector, Instant joinedAt) {
        this.nightId = nightId;
        this.name = name;
        this.answers = answers;
        this.vector = vector;
        this.joinedAt = joinedAt;
    }

    public Long getId() { return id; }
    public Long getNightId() { return nightId; }
    public String getName() { return name; }
    public String getAnswers() { return answers; }
    public String getVector() { return vector; }
    public Instant getJoinedAt() { return joinedAt; }
}
