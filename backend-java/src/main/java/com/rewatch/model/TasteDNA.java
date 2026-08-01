package com.rewatch.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "taste_dna")
public class TasteDNA {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private int comfort;
    private int romance;
    private int slowBurn;
    private int nostalgia;
    private int sadness;

    public TasteDNA() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public int getComfort() { return comfort; }
    public void setComfort(int comfort) { this.comfort = comfort; }

    public int getRomance() { return romance; }
    public void setRomance(int romance) { this.romance = romance; }

    public int getSlowBurn() { return slowBurn; }
    public void setSlowBurn(int slowBurn) { this.slowBurn = slowBurn; }

    public int getNostalgia() { return nostalgia; }
    public void setNostalgia(int nostalgia) { this.nostalgia = nostalgia; }

    public int getSadness() { return sadness; }
    public void setSadness(int sadness) { this.sadness = sadness; }
}