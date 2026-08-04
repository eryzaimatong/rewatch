package com.rewatch.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.rewatch.model.Rating;

@Repository
public interface RatingRepository extends JpaRepository<Rating, Long> {

    /** Replay order. The profile is a pure function of this sequence. */
    List<Rating> findByUserIdOrderByCreatedAtAscIdAsc(Long userId);

    List<Rating> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    long countByUserId(Long userId);

    boolean existsByUserIdAndTitleId(Long userId, Long titleId);

    /** Activity feed: recent ratings from the set of users the caller follows. */
    List<Rating> findByUserIdInOrderByCreatedAtDesc(List<Long> userIds,
            org.springframework.data.domain.Pageable pageable);
}
