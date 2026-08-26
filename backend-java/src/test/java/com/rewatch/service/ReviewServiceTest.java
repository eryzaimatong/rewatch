package com.rewatch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rewatch.model.Rating;
import com.rewatch.model.ReviewComment;
import com.rewatch.model.User;
import com.rewatch.repository.BlockRepository;
import com.rewatch.repository.RatingRepository;
import com.rewatch.repository.ReviewCommentRepository;
import com.rewatch.repository.ReviewLikeRepository;
import com.rewatch.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock private RatingRepository ratingRepo;
    @Mock private UserRepository userRepo;
    @Mock private ReviewLikeRepository reviewLikeRepo;
    @Mock private ReviewCommentRepository reviewCommentRepo;
    @Mock private BlockRepository blockRepo;

    private ReviewService newService() {
        return new ReviewService(ratingRepo, userRepo, reviewLikeRepo, reviewCommentRepo, blockRepo);
    }

    private Rating ratingBy(long id, long userId) {
        Rating r = new Rating();
        r.setId(id);
        r.setUserId(userId);
        r.setTitleId(1L);
        r.setMoment("Loved every minute of this.");
        return r;
    }

    private Rating unpublishedRatingBy(long id, long userId) {
        Rating r = new Rating();
        r.setId(id);
        r.setUserId(userId);
        r.setTitleId(1L);
        return r;
    }

    @Test
    void toggleLikeThrowsWhenEitherSideHasBlocked() {
        when(ratingRepo.findById(10L)).thenReturn(Optional.of(ratingBy(10L, 2L)));
        when(blockRepo.existsByBlockerIdAndBlockedId(1L, 2L)).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> newService().toggleLike(1L, 10L));
        verify(reviewLikeRepo, never()).save(any());
    }

    @Test
    void toggleLikeLikesThenUnlikes() {
        when(ratingRepo.findById(10L)).thenReturn(Optional.of(ratingBy(10L, 2L)));
        when(blockRepo.existsByBlockerIdAndBlockedId(any(), any())).thenReturn(false);
        when(reviewLikeRepo.existsByUserIdAndRatingId(1L, 10L)).thenReturn(false, true);
        when(reviewLikeRepo.countByRatingId(10L)).thenReturn(1L, 0L);

        ReviewService svc = newService();
        ReviewService.LikeResult first = svc.toggleLike(1L, 10L);
        ReviewService.LikeResult second = svc.toggleLike(1L, 10L);

        assertTrue(first.liked());
        assertEquals(1L, first.likeCount());
        assertFalse(second.liked());
        assertEquals(0L, second.likeCount());
        verify(reviewLikeRepo).save(any());
        verify(reviewLikeRepo).deleteByUserIdAndRatingId(1L, 10L);
    }

    @Test
    void addCommentThrowsWhenBlank() {
        assertThrows(IllegalArgumentException.class, () -> newService().addComment(1L, 10L, "   "));
        verify(reviewCommentRepo, never()).save(any());
    }

    @Test
    void addCommentThrowsWhenTooLong() {
        String tooLong = "x".repeat(501);
        assertThrows(IllegalArgumentException.class, () -> newService().addComment(1L, 10L, tooLong));
        verify(reviewCommentRepo, never()).save(any());
    }

    /**
     * The IDOR finding this closes: Rating ids are one global auto-increment
     * sequence, trivially enumerable — liking/commenting used to only check
     * the rating existed, letting any authenticated user confirm a private
     * (blank-moment) rating's existence and spam its owner with a "liked
     * your review" notification for content they never published.
     */
    @Test
    void toggleLikeThrowsOnAnUnpublishedRatingWithTheSameMessageAsUnknown() {
        when(ratingRepo.findById(10L)).thenReturn(Optional.of(unpublishedRatingBy(10L, 2L)));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> newService().toggleLike(1L, 10L));
        assertEquals("Unknown review 10", ex.getMessage());
        verify(reviewLikeRepo, never()).save(any());
        // Never even reaches the block check — the rating isn't treated as
        // "a review" at all, so there's nothing to check callers against.
        verify(blockRepo, never()).existsByBlockerIdAndBlockedId(any(), any());
    }

    @Test
    void toggleLikeThrowsTheIdenticalMessageForATrulyUnknownRatingId() {
        when(ratingRepo.findById(999L)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> newService().toggleLike(1L, 999L));
        assertEquals("Unknown review 999", ex.getMessage());
    }

    @Test
    void addCommentThrowsOnAnUnpublishedRating() {
        when(ratingRepo.findById(10L)).thenReturn(Optional.of(unpublishedRatingBy(10L, 2L)));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> newService().addComment(1L, 10L, "Nice pick!"));
        assertEquals("Unknown review 10", ex.getMessage());
        verify(reviewCommentRepo, never()).save(any());
    }

    @Test
    void addCommentThrowsWhenBlocked() {
        when(ratingRepo.findById(10L)).thenReturn(Optional.of(ratingBy(10L, 2L)));
        when(blockRepo.existsByBlockerIdAndBlockedId(1L, 2L)).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> newService().addComment(1L, 10L, "Nice pick!"));
        verify(reviewCommentRepo, never()).save(any());
    }

    @Test
    void addCommentSucceedsAndTrimsWhitespace() {
        when(ratingRepo.findById(10L)).thenReturn(Optional.of(ratingBy(10L, 2L)));
        when(blockRepo.existsByBlockerIdAndBlockedId(any(), any())).thenReturn(false);
        when(reviewCommentRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReviewComment saved = newService().addComment(1L, 10L, "  Nice pick!  ");

        assertEquals("Nice pick!", saved.getBody());
        assertEquals(1L, saved.getAuthorUserId());
        assertEquals(10L, saved.getRatingId());
    }

    @Test
    void deleteCommentThrowsWhenNotTheAuthor() {
        when(reviewCommentRepo.existsByIdAndAuthorUserId(5L, 1L)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> newService().deleteComment(1L, 5L));
        verify(reviewCommentRepo, never()).deleteById(any());
    }

    @Test
    void deleteCommentSucceedsForTheAuthor() {
        when(reviewCommentRepo.existsByIdAndAuthorUserId(5L, 1L)).thenReturn(true);

        newService().deleteComment(1L, 5L);

        verify(reviewCommentRepo).deleteById(5L);
    }

    @Test
    void listCommentsExcludesBlockedAuthors() {
        ReviewComment fromBlocked = new ReviewComment(10L, 2L, "spam", Instant.now());
        ReviewComment fromFriend = new ReviewComment(10L, 3L, "great review", Instant.now());
        when(reviewCommentRepo.findByRatingIdOrderByCreatedAtAsc(10L)).thenReturn(List.of(fromBlocked, fromFriend));

        User blockedUser = new User();
        blockedUser.setId(2L);
        blockedUser.setUsername("blockedUser");
        User friend = new User();
        friend.setId(3L);
        friend.setUsername("friend");
        when(userRepo.findAllById(any())).thenReturn(List.of(blockedUser, friend));

        when(blockRepo.existsByBlockerIdAndBlockedId(1L, 2L)).thenReturn(true);
        when(blockRepo.existsByBlockerIdAndBlockedId(1L, 3L)).thenReturn(false);
        when(blockRepo.existsByBlockerIdAndBlockedId(3L, 1L)).thenReturn(false);

        List<Map<String, Object>> comments = newService().listComments(10L, 1L);

        assertEquals(1, comments.size());
        assertEquals("friend", comments.get(0).get("authorUsername"));
    }

    @Test
    void withInteractionCountsAnnotatesEachReview() {
        Map<String, Object> review = new LinkedHashMap<>();
        review.put("ratingId", 10L);
        when(reviewLikeRepo.countByRatingId(10L)).thenReturn(3L);
        when(reviewCommentRepo.countByRatingId(10L)).thenReturn(2L);
        when(reviewLikeRepo.existsByUserIdAndRatingId(1L, 10L)).thenReturn(true);

        List<Map<String, Object>> out = newService().withInteractionCounts(List.of(review), 1L);

        assertEquals(3L, out.get(0).get("likeCount"));
        assertEquals(2L, out.get(0).get("commentCount"));
        assertEquals(true, out.get(0).get("likedByCaller"));
    }
}
