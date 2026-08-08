package com.rewatch.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.rewatch.model.ReviewLike;

@Repository
public interface ReviewLikeRepository extends JpaRepository<ReviewLike, Long> {

    boolean existsByUserIdAndRatingId(Long userId, Long ratingId);

    long countByRatingId(Long ratingId);

    void deleteByUserIdAndRatingId(Long userId, Long ratingId);

    /** This user's own likes on other people's reviews — cleared on account deletion. */
    void deleteByUserId(Long userId);

    /** Likes on reviews that are about to be deleted along with their author's account. */
    void deleteByRatingIdIn(List<Long> ratingIds);
}
