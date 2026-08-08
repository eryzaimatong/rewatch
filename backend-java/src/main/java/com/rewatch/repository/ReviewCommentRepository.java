package com.rewatch.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.rewatch.model.ReviewComment;

@Repository
public interface ReviewCommentRepository extends JpaRepository<ReviewComment, Long> {

    List<ReviewComment> findByRatingIdOrderByCreatedAtAsc(Long ratingId);

    long countByRatingId(Long ratingId);

    boolean existsByIdAndAuthorUserId(Long id, Long authorUserId);

    /** This user's own comments on other people's reviews — cleared on account deletion. */
    void deleteByAuthorUserId(Long authorUserId);

    /** Comments on reviews that are about to be deleted along with their author's account. */
    void deleteByRatingIdIn(List<Long> ratingIds);
}
