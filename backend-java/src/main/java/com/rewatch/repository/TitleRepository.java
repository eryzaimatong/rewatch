package com.rewatch.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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
}
