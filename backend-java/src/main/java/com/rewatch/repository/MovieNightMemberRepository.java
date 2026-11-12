package com.rewatch.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.rewatch.model.MovieNightMember;

@Repository
public interface MovieNightMemberRepository extends JpaRepository<MovieNightMember, Long> {

    List<MovieNightMember> findByNightIdOrderByIdAsc(Long nightId);

    long countByNightId(Long nightId);

    @Modifying
    @Transactional
    @Query("delete from MovieNightMember m where m.nightId in (select n.id from MovieNight n where n.expiresAt < :cutoff)")
    int deleteForExpired(java.time.Instant cutoff);
}
