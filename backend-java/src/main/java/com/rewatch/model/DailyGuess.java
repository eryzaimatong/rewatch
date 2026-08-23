package com.rewatch.model;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * One user's answer to one day's "Daily Double Feature" — the Wordle-style
 * daily ritual. titleAId/titleBId are stored (not re-derived from the date
 * on every read) so a later change to the puzzle-generation algorithm can
 * never silently rewrite what a past day actually showed someone.
 */
@Entity
@Table(
    name = "daily_guesses",
    uniqueConstraints = @UniqueConstraint(name = "uk_daily_guess_user_date", columnNames = {"user_id", "play_date"}),
    indexes = @Index(name = "idx_daily_guess_user", columnList = "user_id")
)
public class DailyGuess {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "play_date", nullable = false)
    private LocalDate playDate;

    @Column(name = "title_a_id", nullable = false)
    private Long titleAId;

    @Column(name = "title_b_id", nullable = false)
    private Long titleBId;

    @Column(name = "chosen_title_id", nullable = false)
    private Long chosenTitleId;

    @Column(nullable = false)
    private boolean correct;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public DailyGuess() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long v) { this.userId = v; }

    public LocalDate getPlayDate() { return playDate; }
    public void setPlayDate(LocalDate v) { this.playDate = v; }

    public Long getTitleAId() { return titleAId; }
    public void setTitleAId(Long v) { this.titleAId = v; }

    public Long getTitleBId() { return titleBId; }
    public void setTitleBId(Long v) { this.titleBId = v; }

    public Long getChosenTitleId() { return chosenTitleId; }
    public void setChosenTitleId(Long v) { this.chosenTitleId = v; }

    public boolean isCorrect() { return correct; }
    public void setCorrect(boolean v) { this.correct = v; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { this.createdAt = v; }
}
