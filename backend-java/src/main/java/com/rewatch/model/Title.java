package com.rewatch.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * A film or series in the local catalog.
 *
 * The ten {@code t*} columns hold this title's {@link TraitVector} — its position
 * in the canonical trait space. They are DERIVED from real TMDB content signal
 * (genres, keywords, overview text) by FeatureDeriver, never hand-waved.
 *
 * They are deliberately NEW column names. The previous {@code comfort/romance/
 * slow_burn/nostalgia/sadness} columns are left orphaned in the database because
 * they contain two different scales in the same column — the old CatalogService
 * wrote 0.30 while the old DataSeeder wrote 30 — so no reader could tell which
 * scale a given row used.
 *
 * All new columns are nullable: `ddl-auto=update` cannot add a NOT NULL column to
 * a table that already has rows.
 */
@Entity
@Table(
    name = "titles",
    uniqueConstraints = @UniqueConstraint(name = "uk_titles_tmdb_id", columnNames = "tmdb_id"),
    indexes = {
        @Index(name = "idx_titles_enriched_at", columnList = "enriched_at"),
        @Index(name = "idx_titles_popularity", columnList = "popularity")
    }
)
public class Title {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tmdb_id")
    private Integer tmdbId;

    private String title;
    private int year;
    private String type;
    private String poster;

    @Column(length = 1000)
    private String synopsis;

    private double quality;
    private double popularity;

    // --- Derived trait vector (0..1 each, null until enriched) ---
    @Column(name = "t_family")    private Double tFamily;
    @Column(name = "t_nostalgia") private Double tNostalgia;
    @Column(name = "t_growth")    private Double tGrowth;
    @Column(name = "t_pacing")    private Double tPacing;
    @Column(name = "t_humor")     private Double tHumor;
    @Column(name = "t_romance")   private Double tRomance;
    @Column(name = "t_intensity") private Double tIntensity;
    @Column(name = "t_hope")      private Double tHope;
    @Column(name = "t_bitter")    private Double tBitter;
    @Column(name = "t_comfort")   private Double tComfort;

    /** How much we trust the vector above, 0..1. Drives shrink-toward-neutral. */
    @Column(name = "feature_confidence")
    private Double featureConfidence;

    /** TMDB_KEYWORDS | OVERVIEW | GENRE_ONLY | DEFAULT — see FeaturesSource. */
    @Column(name = "features_source", length = 16)
    private String featuresSource;

    /** Bumped when the lexicon changes, so stale vectors can be found and redone. */
    @Column(name = "lexicon_version")
    private Integer lexiconVersion;

    // --- Cached raw TMDB signal, so the lexicon can be re-tuned with zero API calls ---
    @Column(name = "keyword_names", length = 2000)
    private String keywordNames;

    @Column(name = "genre_ids", length = 120)
    private String genreIds;

    @Column(name = "original_language", length = 8)
    private String originalLanguage;

    @Column(name = "vote_average")
    private Double voteAverage;

    @Column(name = "vote_count")
    private Integer voteCount;

    @Column(name = "enriched_at")
    private Instant enrichedAt;

    public Title() {}

    /** The derived vector, or the neutral vector if this title is not enriched yet. */
    public TraitVector traitVector() {
        if (tFamily == null) {
            return TraitVector.neutral();
        }
        double[] v = new double[Trait.count()];
        v[Trait.FAMILY.ordinal()]    = nz(tFamily);
        v[Trait.NOSTALGIA.ordinal()] = nz(tNostalgia);
        v[Trait.GROWTH.ordinal()]    = nz(tGrowth);
        v[Trait.PACING.ordinal()]    = nz(tPacing);
        v[Trait.HUMOR.ordinal()]     = nz(tHumor);
        v[Trait.ROMANCE.ordinal()]   = nz(tRomance);
        v[Trait.INTENSITY.ordinal()] = nz(tIntensity);
        v[Trait.HOPE.ordinal()]      = nz(tHope);
        v[Trait.BITTER.ordinal()]    = nz(tBitter);
        v[Trait.COMFORT.ordinal()]   = nz(tComfort);
        return TraitVector.of(v);
    }

    public void setTraitVector(TraitVector vec) {
        tFamily    = vec.get(Trait.FAMILY);
        tNostalgia = vec.get(Trait.NOSTALGIA);
        tGrowth    = vec.get(Trait.GROWTH);
        tPacing    = vec.get(Trait.PACING);
        tHumor     = vec.get(Trait.HUMOR);
        tRomance   = vec.get(Trait.ROMANCE);
        tIntensity = vec.get(Trait.INTENSITY);
        tHope      = vec.get(Trait.HOPE);
        tBitter    = vec.get(Trait.BITTER);
        tComfort   = vec.get(Trait.COMFORT);
    }

    private static double nz(Double d) {
        return d == null ? TraitVector.NEUTRAL : d;
    }

    /** Audience quality normalised to 0..1, used only as a low-confidence prior. */
    public double normalisedQuality() {
        if (voteAverage != null && voteCount != null && voteCount > 0) {
            return TraitVector.clamp(voteAverage / 10.0);
        }
        if (quality > 0) {
            return TraitVector.clamp(quality / 5.0);
        }
        return 0.5;
    }

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

    public double getQuality() { return quality; }
    public void setQuality(double quality) { this.quality = quality; }

    public double getPopularity() { return popularity; }
    public void setPopularity(double popularity) { this.popularity = popularity; }

    public Double getFeatureConfidence() { return featureConfidence; }
    public void setFeatureConfidence(Double v) { this.featureConfidence = v; }

    public String getFeaturesSource() { return featuresSource; }
    public void setFeaturesSource(String v) { this.featuresSource = v; }

    public Integer getLexiconVersion() { return lexiconVersion; }
    public void setLexiconVersion(Integer v) { this.lexiconVersion = v; }

    public String getKeywordNames() { return keywordNames; }
    public void setKeywordNames(String v) { this.keywordNames = v; }

    public String getGenreIds() { return genreIds; }
    public void setGenreIds(String v) { this.genreIds = v; }

    public String getOriginalLanguage() { return originalLanguage; }
    public void setOriginalLanguage(String v) { this.originalLanguage = v; }

    public Double getVoteAverage() { return voteAverage; }
    public void setVoteAverage(Double v) { this.voteAverage = v; }

    public Integer getVoteCount() { return voteCount; }
    public void setVoteCount(Integer v) { this.voteCount = v; }

    public Instant getEnrichedAt() { return enrichedAt; }
    public void setEnrichedAt(Instant v) { this.enrichedAt = v; }
}
