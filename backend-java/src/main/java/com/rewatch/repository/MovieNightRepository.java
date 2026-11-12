package com.rewatch.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.rewatch.model.MovieNight;

@Repository
public interface MovieNightRepository extends JpaRepository<MovieNight, Long> {

    Optional<MovieNight> findByCode(String code);

    @Modifying
    @Transactional
    @Query("delete from MovieNight n where n.expiresAt < :cutoff")
    int deleteExpired(Instant cutoff);
}
