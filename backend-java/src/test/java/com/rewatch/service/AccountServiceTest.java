package com.rewatch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.rewatch.model.Rating;
import com.rewatch.model.User;
import com.rewatch.model.WatchlistFolder;
import com.rewatch.repository.FollowRepository;
import com.rewatch.repository.RatingRepository;
import com.rewatch.repository.TitleRepository;
import com.rewatch.repository.TraitEventRepository;
import com.rewatch.repository.UserRepository;
import com.rewatch.repository.UserTraitRepository;
import com.rewatch.repository.WatchlistFolderRepository;
import com.rewatch.repository.WatchlistItemRepository;

/**
 * setAccentColor and setAvatarUrl are the two places a client-supplied string
 * reaches a column with real consequences if unvalidated — an unknown accent
 * just means "no theme applies," but an unbounded avatar payload could bloat
 * every row read alongside it, and a non-image URL would break every <img>
 * that renders it.
 */
@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock private UserRepository userRepo;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private RatingRepository ratingRepo;
    @Mock private FollowRepository followRepo;
    @Mock private WatchlistItemRepository watchlistItemRepo;
    @Mock private WatchlistFolderRepository watchlistFolderRepo;
    @Mock private TitleRepository titleRepo;
    @Mock private UserTraitRepository userTraitRepo;
    @Mock private TraitEventRepository traitEventRepo;

    private AccountService newService() {
        return new AccountService(userRepo, passwordEncoder, null, ratingRepo, null, watchlistFolderRepo, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    /**
     * AchievementService is a concrete class — this JDK/Mockito combination
     * can only mock interfaces (see RecommenderTest et al for the same
     * constraint), so setAvatarFrame's tests get a real instance built from
     * mocked repos, same pattern as AchievementServiceTest itself.
     */
    private AccountService newServiceWithRealAchievements() {
        ProfileService profileService = new ProfileService(
                ratingRepo, titleRepo, userTraitRepo, traitEventRepo, userRepo, new VectorEngine());
        AchievementService achievementService = new AchievementService(
                ratingRepo, followRepo, watchlistItemRepo, userRepo, profileService, new StreakService(ratingRepo));
        return new AccountService(userRepo, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, achievementService, null);
    }

    @Test
    void acceptsAKnownAccentColor() {
        User user = new User();
        when(userRepo.findById(1L)).thenReturn(Optional.of(user));

        newService().setAccentColor(1L, "blue");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        org.mockito.Mockito.verify(userRepo).save(captor.capture());
        assertEquals("blue", captor.getValue().getAccentColor());
    }

    @Test
    void rejectsAnUnknownAccentColor() {
        assertThrows(IllegalArgumentException.class, () -> newService().setAccentColor(1L, "cyberpunk-neon"));
    }

    @Test
    void acceptsAReasonablySizedImageDataUrl() {
        User user = new User();
        when(userRepo.findById(1L)).thenReturn(Optional.of(user));
        String smallDataUrl = "data:image/jpeg;base64,/9j/4AAQSkZJRg=="; // tiny, well under the cap

        newService().setAvatarUrl(1L, smallDataUrl);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        org.mockito.Mockito.verify(userRepo).save(captor.capture());
        assertEquals(smallDataUrl, captor.getValue().getAvatarUrl());
    }

    @Test
    void rejectsAnAvatarThatIsNotAnImageDataUrl() {
        User user = new User();
        when(userRepo.findById(1L)).thenReturn(Optional.of(user));

        assertThrows(IllegalArgumentException.class,
                () -> newService().setAvatarUrl(1L, "https://evil.example/track.png"));
    }

    @Test
    void rejectsAnAvatarOverTheSizeCeiling() {
        User user = new User();
        when(userRepo.findById(1L)).thenReturn(Optional.of(user));
        // MAX_AVATAR_BYTES is 400_000; a base64 payload needs ~4/3 that many
        // characters to decode past it.
        String hugePayload = "a".repeat(600_000);

        assertThrows(IllegalArgumentException.class,
                () -> newService().setAvatarUrl(1L, "data:image/jpeg;base64," + hugePayload));
    }

    @Test
    void blankAvatarClearsTheExistingOne() {
        User user = new User();
        user.setAvatarUrl("data:image/jpeg;base64,abc");
        when(userRepo.findById(1L)).thenReturn(Optional.of(user));

        newService().setAvatarUrl(1L, "");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        org.mockito.Mockito.verify(userRepo).save(captor.capture());
        assertEquals(null, captor.getValue().getAvatarUrl());
    }

    @Test
    void setsAFrameWhenItsRequiredAchievementIsUnlocked() {
        User user = new User();
        when(userRepo.findById(1L)).thenReturn(Optional.of(user));
        when(userTraitRepo.findByUserId(1L)).thenReturn(java.util.List.of());
        when(ratingRepo.countByUserId(1L)).thenReturn(15L); // unlocks rating_15 -> "bronze"

        newServiceWithRealAchievements().setAvatarFrame(1L, "bronze");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        org.mockito.Mockito.verify(userRepo).save(captor.capture());
        assertEquals("bronze", captor.getValue().getAvatarFrame());
    }

    @Test
    void rejectsAFrameWhoseRequiredAchievementIsntUnlockedYet() {
        User user = new User();
        when(userRepo.findById(1L)).thenReturn(Optional.of(user));
        when(userTraitRepo.findByUserId(1L)).thenReturn(java.util.List.of());
        when(ratingRepo.countByUserId(1L)).thenReturn(0L); // nowhere near rating_100 -> "gold"

        assertThrows(IllegalArgumentException.class, () -> newServiceWithRealAchievements().setAvatarFrame(1L, "gold"));
    }

    @Test
    void rejectsAnUnknownFrameKey() {
        User user = new User();
        when(userRepo.findById(1L)).thenReturn(Optional.of(user));

        assertThrows(IllegalArgumentException.class, () -> newService().setAvatarFrame(1L, "made-up-frame"));
    }

    @Test
    void blankFrameClearsTheExistingOne() {
        User user = new User();
        user.setAvatarFrame("bronze");
        when(userRepo.findById(1L)).thenReturn(Optional.of(user));

        newService().setAvatarFrame(1L, "");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        org.mockito.Mockito.verify(userRepo).save(captor.capture());
        assertNull(captor.getValue().getAvatarFrame());
    }

    @Test
    void pinsTitlesTheUserHasActuallyRated() {
        User user = new User();
        when(userRepo.findById(1L)).thenReturn(Optional.of(user));
        when(ratingRepo.existsByUserIdAndTitleId(1L, 10L)).thenReturn(true);
        when(ratingRepo.existsByUserIdAndTitleId(1L, 20L)).thenReturn(true);

        newService().setPinnedContent(1L, java.util.List.of(10L, 20L), null, null);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        org.mockito.Mockito.verify(userRepo).save(captor.capture());
        assertEquals("10,20", captor.getValue().getPinnedTitleIds());
    }

    @Test
    void rejectsPinningATitleTheUserNeverRated() {
        User user = new User();
        when(userRepo.findById(1L)).thenReturn(Optional.of(user));
        when(ratingRepo.existsByUserIdAndTitleId(1L, 10L)).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> newService().setPinnedContent(1L, java.util.List.of(10L), null, null));
    }

    @Test
    void rejectsPinningMoreThanFourTitles() {
        User user = new User();
        when(userRepo.findById(1L)).thenReturn(Optional.of(user));

        assertThrows(IllegalArgumentException.class,
                () -> newService().setPinnedContent(1L, java.util.List.of(1L, 2L, 3L, 4L, 5L), null, null));
    }

    @Test
    void pinsOwnReviewWithARealMoment() {
        User user = new User();
        when(userRepo.findById(1L)).thenReturn(Optional.of(user));
        Rating rating = new Rating();
        rating.setUserId(1L);
        rating.setMoment("Ending Payoff");
        when(ratingRepo.findById(99L)).thenReturn(Optional.of(rating));

        newService().setPinnedContent(1L, null, 99L, null);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        org.mockito.Mockito.verify(userRepo).save(captor.capture());
        assertEquals(99L, captor.getValue().getPinnedRatingId());
    }

    @Test
    void rejectsPinningSomeoneElsesReview() {
        User user = new User();
        when(userRepo.findById(1L)).thenReturn(Optional.of(user));
        Rating rating = new Rating();
        rating.setUserId(2L); // not the caller
        rating.setMoment("Ending Payoff");
        when(ratingRepo.findById(99L)).thenReturn(Optional.of(rating));

        assertThrows(IllegalArgumentException.class, () -> newService().setPinnedContent(1L, null, 99L, null));
    }

    @Test
    void pinsOwnFolder() {
        User user = new User();
        when(userRepo.findById(1L)).thenReturn(Optional.of(user));
        when(watchlistFolderRepo.existsByIdAndUserId(5L, 1L)).thenReturn(true);

        newService().setPinnedContent(1L, null, null, 5L);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        org.mockito.Mockito.verify(userRepo).save(captor.capture());
        assertEquals(5L, captor.getValue().getPinnedFolderId());
    }

    @Test
    void rejectsPinningSomeoneElsesFolder() {
        User user = new User();
        when(userRepo.findById(1L)).thenReturn(Optional.of(user));
        when(watchlistFolderRepo.existsByIdAndUserId(5L, 1L)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> newService().setPinnedContent(1L, null, null, 5L));
    }

    @Test
    void allThreeNullsClearEveryPin() {
        User user = new User();
        user.setPinnedTitleIds("10,20");
        user.setPinnedRatingId(99L);
        user.setPinnedFolderId(5L);
        when(userRepo.findById(1L)).thenReturn(Optional.of(user));

        newService().setPinnedContent(1L, null, null, null);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        org.mockito.Mockito.verify(userRepo).save(captor.capture());
        assertNull(captor.getValue().getPinnedTitleIds());
        assertNull(captor.getValue().getPinnedRatingId());
        assertNull(captor.getValue().getPinnedFolderId());
    }

    /**
     * changeEmail exists specifically to make an account recoverable again —
     * every one of these guards matters for that one job: wrong password
     * blocks a stolen token from hijacking recovery, an invalid format would
     * silently recreate the exact unrecoverable state this fixes, and letting
     * a duplicate email through would break the uniqueness the whole
     * forgot-password lookup depends on.
     */
    @Test
    void changeEmailRequiresTheCorrectCurrentPassword() {
        User user = new User();
        user.setPassword("hashed");
        when(userRepo.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> newService().changeEmail(1L, "wrong", "real@example.com"));
    }

    @Test
    void changeEmailRejectsAnInvalidFormat() {
        User user = new User();
        user.setPassword("hashed");
        when(userRepo.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct", "hashed")).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> newService().changeEmail(1L, "correct", "not-an-email"));
    }

    @Test
    void changeEmailRejectsOneAlreadyRegisteredToSomeoneElse() {
        User user = new User();
        user.setPassword("hashed");
        when(userRepo.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct", "hashed")).thenReturn(true);
        User otherAccount = new User();
        otherAccount.setId(2L);
        when(userRepo.findByEmail("taken@example.com")).thenReturn(otherAccount);

        assertThrows(IllegalArgumentException.class,
                () -> newService().changeEmail(1L, "correct", "taken@example.com"));
    }

    @Test
    void changeEmailSucceedsWithAValidNewAddress() {
        User user = new User();
        user.setId(1L);
        user.setPassword("hashed");
        user.setEmail("1@rewatch.local");
        when(userRepo.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct", "hashed")).thenReturn(true);
        when(userRepo.findByEmail("real@example.com")).thenReturn(null);

        newService().changeEmail(1L, "correct", "real@example.com");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        org.mockito.Mockito.verify(userRepo).save(captor.capture());
        assertEquals("real@example.com", captor.getValue().getEmail());
    }
}
