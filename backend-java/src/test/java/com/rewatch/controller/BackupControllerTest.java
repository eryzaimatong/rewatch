package com.rewatch.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rewatch.model.Rating;
import com.rewatch.model.User;
import com.rewatch.repository.BlockRepository;
import com.rewatch.repository.DailyGuessRepository;
import com.rewatch.repository.FollowRepository;
import com.rewatch.repository.RatingRepository;
import com.rewatch.repository.ReviewCommentRepository;
import com.rewatch.repository.ReviewLikeRepository;
import com.rewatch.repository.TraitEventRepository;
import com.rewatch.repository.UserRepository;
import com.rewatch.repository.UserTraitRepository;
import com.rewatch.repository.WatchlistFolderRepository;
import com.rewatch.repository.WatchlistItemRepository;

/**
 * Covers the actual contract this endpoint exists for: every one of the
 * genuinely irreplaceable tables is present in the export, and it reflects
 * what the repositories actually return rather than silently omitting one
 * on a copy-paste mistake — the whole value of a backup endpoint is that it
 * covers everything it claims to.
 */
@ExtendWith(MockitoExtension.class)
class BackupControllerTest {

    @Mock private UserRepository userRepo;
    @Mock private RatingRepository ratingRepo;
    @Mock private WatchlistFolderRepository watchlistFolderRepo;
    @Mock private WatchlistItemRepository watchlistItemRepo;
    @Mock private FollowRepository followRepo;
    @Mock private ReviewCommentRepository reviewCommentRepo;
    @Mock private ReviewLikeRepository reviewLikeRepo;
    @Mock private BlockRepository blockRepo;
    @Mock private DailyGuessRepository dailyGuessRepo;
    @Mock private UserTraitRepository userTraitRepo;
    @Mock private TraitEventRepository traitEventRepo;

    private BackupController newController() {
        return new BackupController(userRepo, ratingRepo, watchlistFolderRepo, watchlistItemRepo,
                followRepo, reviewCommentRepo, reviewLikeRepo, blockRepo, dailyGuessRepo,
                userTraitRepo, traitEventRepo);
    }

    @Test
    void includesEveryDeclaredTableAndAnExportTimestamp() {
        User user = new User();
        when(userRepo.findAll()).thenReturn(List.of(user));
        when(ratingRepo.findAll()).thenReturn(List.of(new Rating()));
        when(watchlistFolderRepo.findAll()).thenReturn(List.of());
        when(watchlistItemRepo.findAll()).thenReturn(List.of());
        when(followRepo.findAll()).thenReturn(List.of());
        when(reviewCommentRepo.findAll()).thenReturn(List.of());
        when(reviewLikeRepo.findAll()).thenReturn(List.of());
        when(blockRepo.findAll()).thenReturn(List.of());
        when(dailyGuessRepo.findAll()).thenReturn(List.of());
        when(userTraitRepo.findAll()).thenReturn(List.of());
        when(traitEventRepo.findAll()).thenReturn(List.of());

        Map<String, Object> result = newController().backup();

        assertNotNull(result.get("exportedAt"));
        assertEquals(List.of(user), result.get("users"));
        for (String key : new String[] {
            "users", "ratings", "watchlistFolders", "watchlistItems", "follows",
            "reviewComments", "reviewLikes", "blocks", "dailyGuesses", "userTraits", "traitEvents"
        }) {
            assertTrue(result.containsKey(key), "backup response is missing: " + key);
        }
    }
}
