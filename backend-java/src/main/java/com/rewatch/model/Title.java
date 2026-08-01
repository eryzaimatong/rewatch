package com.rewatch.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "titles")
public class Title {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer tmdbId;
    private String title;
    private int year;
    private String type;
    private String poster;

    @Column(length = 1000)
    private String synopsis;

    @Column(length = 2000)
    private String storyprint;

    private double quality;
    private double popularity;

    private double comfort;
    private double romance;
    private double slowBurn;
    private double nostalgia;
    private double sadness;

    public Title() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Integer getTmdbId() { return tmdbId; }
    public void setTmdbId(Integer tmdbId) { this.tmdbId = tmdbId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getPoster() { return poster; }
    public void setPoster(String poster) { this.poster = poster; }

    public String getSynopsis() { return synopsis; }
    public void setSynopsis(String synopsis) { this.synopsis = synopsis; }

    public String getStoryprint() { return storyprint; }
    public void setStoryprint(String storyprint) { this.storyprint = storyprint; }

    public double getQuality() { return quality; }
    public void setQuality(double quality) { this.quality = quality; }

    public double getPopularity() { return popularity; }
    public void setPopularity(double popularity) { this.popularity = popularity; }

    public double getComfort() { return comfort; }
    public void setComfort(double comfort) { this.comfort = comfort; }

    public double getRomance() { return romance; }
    public void setRomance(double romance) { this.romance = romance; }

    public double getSlowBurn() { return slowBurn; }
    public void setSlowBurn(double slowBurn) { this.slowBurn = slowBurn; }

    public double getNostalgia() { return nostalgia; }
    public void setNostalgia(double nostalgia) { this.nostalgia = nostalgia; }

    public double getSadness() { return sadness; }
    public void setSadness(double sadness) { this.sadness = sadness; }
}