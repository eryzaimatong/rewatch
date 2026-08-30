package com.rewatch.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.rewatch.model.Title;

@Repository
public interface TitleRepository extends JpaRepository<Title, Long> {

    Optional<Title> findByTmdbId(Integer tmdbId);

    List<Title> findByTmdbIdIn(List<Integer> tmdbIds);

    /** Legacy rows written by the old DataSeeder, which never set a tmdbId. */
    List<Title> findByTmdbIdIsNull();

    /** Enrichment queue: titles whose trait vector has not been derived yet. */
    @Query("select t from Title t where t.enrichedAt is null order by t.popularity desc")
    List<Title> findUnenriched(Pageable pageable);

    long countByEnrichedAtIsNotNull();

    /**
     * Server-side counterpart of TitleController.getAllTitles's
     * hasRenderableData + well-known-per-type filtering, scoped to a single
     * requested bucket (movie/anime/"show" = everything else) and paged —
     * see TitleController.picker for why this exists instead of shipping the
     * whole catalog. `search` is passed as `''` for "no filter", not null,
     * since JPQL string comparison against a null parameter never matches.
     */
    @Query("select t from Title t where t.synopsis is not null and t.synopsis <> '' "
            + "and t.poster is not null and t.poster <> '' "
            + "and ((:bucket = 'show' and t.type not in ('movie','anime')) or t.type = :bucket) "
            + "and t.voteCount >= :minVotes "
            + "and (:search = '' or lower(t.title) like lower(concat('%', :search, '%'))) "
            + "order by t.popularity desc")
    Page<Title> findWellKnownForPicker(@Param("bucket") String bucket, @Param("minVotes") int minVotes,
                                        @Param("search") String search, Pageable pageable);

    /** Same bucketing/search as findWellKnownForPicker, without the vote-count floor — the thin-bucket fallback. */
    @Query("select t from Title t where t.synopsis is not null and t.synopsis <> '' "
            + "and t.poster is not null and t.poster <> '' "
            + "and ((:bucket = 'show' and t.type not in ('movie','anime')) or t.type = :bucket) "
            + "and (:search = '' or lower(t.title) like lower(concat('%', :search, '%'))) "
            + "order by t.popularity desc")
    Page<Title> findAllForPicker(@Param("bucket") String bucket, @Param("search") String search, Pageable pageable);

    /** Forces already-selected favourites to stay visible even if a later search/page would otherwise cut them off. */
    List<Title> findByTitleIn(List<String> titles);

    /**
     * Targeted lookup for OnboardingService.deriveSeed's favourites matching
     * — replaces a titleRepo.findAll() + linear scan that ran on every
     * onboarding submission (including a "Skip for now" with zero
     * favourites) to find at most 5 titles by name. `needles` must already
     * be trimmed and lower-cased by the caller, matching the exact
     * trim+lowercase equality the old in-memory findByTitle used — a
     * substring/LIKE match here would change which title an ambiguous name
     * resolves to.
     */
    @Query("select t from Title t where lower(trim(t.title)) in :needles")
    List<Title> findByTitleTrimmedLowerIn(@Param("needles") List<String> needles);
}
