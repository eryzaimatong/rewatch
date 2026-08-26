package com.rewatch.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rewatch.model.Rating;
import com.rewatch.model.ReviewComment;
import com.rewatch.model.User;
import com.rewatch.repository.BlockRepository;
import com.rewatch.repository.RatingRepository;
import com.rewatch.repository.ReviewCommentRepository;
import com.rewatch.repository.ReviewLikeRepository;
import com.rewatch.repository.UserRepository;

/**
 * Likes and comments on a review — a review being a {@link Rating} with a
 * non-blank `moment`, the same thing SocialService.reviews()/activityFeed()
 * already surface. Kept as its own service rather than folded into
 * SocialService (already large, and three other test files construct it
 * directly — adding dependencies there ripples into all of them for no
 * benefit here).
 */
@Service
public class ReviewService {

    private static final int MAX_COMMENT_LENGTH = 500;

    private final RatingRepository ratingRepo;
    private final UserRepository userRepo;
    private final ReviewLikeRepository reviewLikeRepo;
    private final ReviewCommentRepository reviewCommentRepo;
    private final BlockRepository blockRepo;

    public ReviewService(RatingRepository ratingRepo, UserRepository userRepo,
                         ReviewLikeRepository reviewLikeRepo, ReviewCommentRepository reviewCommentRepo,
                         BlockRepository blockRepo) {
        this.ratingRepo = ratingRepo;
        this.userRepo = userRepo;
        this.reviewLikeRepo = reviewLikeRepo;
        this.reviewCommentRepo = reviewCommentRepo;
        this.blockRepo = blockRepo;
    }

    private boolean isBlocked(Long a, Long b) {
        return blockRepo.existsByBlockerIdAndBlockedId(a, b) || blockRepo.existsByBlockerIdAndBlockedId(b, a);
    }

    /**
     * A blank-moment Rating is private telemetry, never published as a
     * review (see the class doc comment and SocialService.reviews()) — but
     * Rating ids are one global auto-increment sequence, trivially
     * enumerable by any authenticated user. Liking/commenting used to only
     * check the rating existed, which let anyone confirm a specific
     * private rating's existence (and spam its owner with a "liked your
     * review of X" notification for content they never published) just by
     * guessing ids. Throwing the identical message/shape as "doesn't
     * exist" here means a blank-moment rating and a genuinely unknown id
     * are indistinguishable to the caller.
     */
    private Rating requirePublishedReview(Long ratingId) {
        Rating rating = ratingRepo.findById(ratingId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown review " + ratingId));
        if (rating.getMoment() == null || rating.getMoment().isBlank()) {
            throw new IllegalArgumentException("Unknown review " + ratingId);
        }
        return rating;
    }

    /**
     * Annotates an already-built review list (SocialService.reviews()/
     * activityFeed()'s output shape — each entry a Map with "ratingId") with
     * likeCount/commentCount/likedByCaller, without SocialService needing to
     * know this feature exists.
     */
    public List<Map<String, Object>> withInteractionCounts(List<Map<String, Object>> reviews, Long callerId) {
        for (Map<String, Object> review : reviews) {
            Long ratingId = (Long) review.get("ratingId");
            review.put("likeCount", reviewLikeRepo.countByRatingId(ratingId));
            review.put("commentCount", reviewCommentRepo.countByRatingId(ratingId));
            review.put("likedByCaller", callerId != null && reviewLikeRepo.existsByUserIdAndRatingId(callerId, ratingId));
        }
        return reviews;
    }

    public record LikeResult(boolean liked, long likeCount) {}

    @Transactional
    public LikeResult toggleLike(Long callerId, Long ratingId) {
        Rating rating = requirePublishedReview(ratingId);
        if (isBlocked(callerId, rating.getUserId())) {
            throw new IllegalArgumentException("Cannot interact with this review.");
        }

        boolean alreadyLiked = reviewLikeRepo.existsByUserIdAndRatingId(callerId, ratingId);
        if (alreadyLiked) {
            reviewLikeRepo.deleteByUserIdAndRatingId(callerId, ratingId);
        } else {
            reviewLikeRepo.save(new com.rewatch.model.ReviewLike(callerId, ratingId, Instant.now()));
        }
        return new LikeResult(!alreadyLiked, reviewLikeRepo.countByRatingId(ratingId));
    }

    /** Non-null only the instant a like is newly created — the controller uses this to decide whether to notify. */
    public Rating ratingFor(Long ratingId) {
        return ratingRepo.findById(ratingId).orElse(null);
    }

    public List<Map<String, Object>> listComments(Long ratingId, Long callerId) {
        List<ReviewComment> comments = reviewCommentRepo.findByRatingIdOrderByCreatedAtAsc(ratingId);
        if (comments.isEmpty()) {
            return List.of();
        }
        List<Long> authorIds = comments.stream().map(ReviewComment::getAuthorUserId).distinct().toList();
        Map<Long, User> authors = new HashMap<>();
        userRepo.findAllById(authorIds).forEach(u -> authors.put(u.getId(), u));

        List<Map<String, Object>> out = new ArrayList<>();
        for (ReviewComment c : comments) {
            if (callerId != null && isBlocked(callerId, c.getAuthorUserId())) {
                continue;
            }
            User author = authors.get(c.getAuthorUserId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("authorUserId", c.getAuthorUserId());
            m.put("authorUsername", author == null ? "Unknown" : author.getUsername());
            m.put("body", c.getBody());
            m.put("createdAt", c.getCreatedAt());
            m.put("isOwn", callerId != null && callerId.equals(c.getAuthorUserId()));
            m.put("hasSpoilers", c.isHasSpoilers());
            out.add(m);
        }
        return out;
    }

    @Transactional
    public ReviewComment addComment(Long callerId, Long ratingId, String body) {
        return addComment(callerId, ratingId, body, false);
    }

    @Transactional
    public ReviewComment addComment(Long callerId, Long ratingId, String body, boolean hasSpoilers) {
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("Comment can't be empty.");
        }
        String trimmed = body.trim();
        if (trimmed.length() > MAX_COMMENT_LENGTH) {
            throw new IllegalArgumentException("Comment is too long (max " + MAX_COMMENT_LENGTH + " characters).");
        }
        Rating rating = requirePublishedReview(ratingId);
        if (isBlocked(callerId, rating.getUserId())) {
            throw new IllegalArgumentException("Cannot interact with this review.");
        }

        ReviewComment comment = new ReviewComment(ratingId, callerId, trimmed, Instant.now());
        comment.setHasSpoilers(hasSpoilers);
        return reviewCommentRepo.save(comment);
    }

    @Transactional
    public void deleteComment(Long callerId, Long commentId) {
        if (!reviewCommentRepo.existsByIdAndAuthorUserId(commentId, callerId)) {
            throw new IllegalArgumentException("Not your comment to delete.");
        }
        reviewCommentRepo.deleteById(commentId);
    }
}
