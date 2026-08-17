package com.rewatch.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.rewatch.model.Rating;

@Repository
public interface RatingRepository extends JpaRepository<Rating, Long> {

    /** Replay order. The profile is a pure function of this sequence. */
    List<Rating> findByUserIdOrderByCreatedAtAscIdAsc(Long userId);

    List<Rating> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    /** Bounded variant for SocialService.reviews — a long-time user's whole rating log shouldn't load into memory on every profile view. */
    List<Rating> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, org.springframework.data.domain.Pageable pageable);

    long countByUserId(Long userId);

    boolean existsByUserIdAndTitleId(Long userId, Long titleId);

    boolean existsByIdAndUserId(Long id, Long userId);

    /** Activity feed: recent ratings from the set of users the caller follows. */
    List<Rating> findByUserIdInOrderByCreatedAtDesc(List<Long> userIds,
            org.springframework.data.domain.Pageable pageable);

    /** How many times this user has rated this title before — 0 means "first watch." */
    long countByUserIdAndTitleId(Long userId, Long titleId);

    /** The most recent prior rating of this title by this user, for rewatch-delta messaging. */
    Optional<Rating> findFirstByUserIdAndTitleIdOrderByCreatedAtDescIdDesc(Long userId, Long titleId);

    /** Every title id this user has ever rated — the "WATCHED" fallback for WatchStatusService. */
    @Query("select distinct r.titleId from Rating r where r.userId = :userId")
    List<Long> findDistinctTitleIdsByUserId(Long userId);

    void deleteByUserId(Long userId);
}
