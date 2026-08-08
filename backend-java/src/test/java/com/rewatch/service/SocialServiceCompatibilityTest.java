package com.rewatch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rewatch.model.Trait;
import com.rewatch.model.TraitNode;
import com.rewatch.model.User;
import com.rewatch.model.UserTrait;
import com.rewatch.repository.BlockRepository;
import com.rewatch.repository.FollowRepository;
import com.rewatch.repository.RatingRepository;
import com.rewatch.repository.TitleRepository;
import com.rewatch.repository.TraitEventRepository;
import com.rewatch.repository.UserRepository;
import com.rewatch.repository.UserTraitRepository;
import com.rewatch.repository.WatchlistFolderRepository;
import com.rewatch.repository.WatchlistItemRepository;

/**
 * The pairwise compatibility % shown on an individual profile page (previously
 * only visible in the ranked Community match list) must use the same
 * evidence floor as dnaMatches — under 3 ratings on either side, the profile
 * is still mostly the neutral 0.5 default, so the "score" would just be a
 * flat-line artifact rather than a real taste signal.
 */
@ExtendWith(MockitoExtension.class)
class SocialServiceCompatibilityTest {

    @Mock private UserRepository userRepo;
    @Mock private FollowRepository followRepo;
    @Mock private BlockRepository blockRepo;
    @Mock private RatingRepository ratingRepo;
    @Mock private TitleRepository titleRepo;
    @Mock private WatchlistFolderRepository folderRepo;
    @Mock private WatchlistItemRepository itemRepo;
    @Mock private UserTraitRepository userTraitRepo;
    @Mock private TraitEventRepository traitEventRepo;

    private SocialService newService() {
        ProfileService profileService = new ProfileService(
                ratingRepo, titleRepo, userTraitRepo, traitEventRepo, userRepo, new VectorEngine());
        return new SocialService(userRepo, followRepo, blockRepo, ratingRepo, titleRepo,
                folderRepo, itemRepo, profileService, new ArchetypeService(), null);
    }

    private User publicUserNamed(String username) {
        User u = new User();
        u.setUsername(username);
        u.setProfilePublic(true);
        return u;
    }

    @Test
    void nullWhenViewingYourOwnProfile() {
        when(userRepo.findById(1L)).thenReturn(Optional.of(publicUserNamed("me")));
        when(ratingRepo.countByUserId(1L)).thenReturn(10L);
        when(userTraitRepo.findByUserId(1L)).thenReturn(List.of());

        var res = newService().publicProfile(1L, 1L);

        assertNull(res.get("compatibilityScore"));
    }

    @Test
    void nullWhenCallerIsUnderTheRatingFloor() {
        when(userRepo.findById(2L)).thenReturn(Optional.of(publicUserNamed("target")));
        when(ratingRepo.countByUserId(2L)).thenReturn(10L);
        when(ratingRepo.countByUserId(1L)).thenReturn(2L);
        when(userTraitRepo.findByUserId(2L)).thenReturn(List.of());

        var res = newService().publicProfile(2L, 1L);

        assertNull(res.get("compatibilityScore"));
    }

    @Test
    void nullWhenTargetIsUnderTheRatingFloor() {
        when(userRepo.findById(2L)).thenReturn(Optional.of(publicUserNamed("target")));
        when(ratingRepo.countByUserId(2L)).thenReturn(1L);
        when(ratingRepo.countByUserId(1L)).thenReturn(10L);
        when(userTraitRepo.findByUserId(2L)).thenReturn(List.of());

        var res = newService().publicProfile(2L, 1L);

        assertNull(res.get("compatibilityScore"));
    }

    @Test
    void computedWhenBothSidesClearTheFloor() {
        when(userRepo.findById(2L)).thenReturn(Optional.of(publicUserNamed("target")));
        when(ratingRepo.countByUserId(2L)).thenReturn(5L);
        when(ratingRepo.countByUserId(1L)).thenReturn(5L);

        UserTrait targetFamily = new UserTrait(2L, Trait.FAMILY, new TraitNode(0.9, 0.8, 0, 5, Instant.now()));
        when(userTraitRepo.findByUserId(2L)).thenReturn(List.of(targetFamily));
        UserTrait callerFamily = new UserTrait(1L, Trait.FAMILY, new TraitNode(0.2, 0.8, 0, 5, Instant.now()));
        when(userTraitRepo.findByUserId(1L)).thenReturn(List.of(callerFamily));

        var res = newService().publicProfile(2L, 1L);

        Object score = res.get("compatibilityScore");
        assertNotNull(score, "opposite FAMILY preferences on otherwise-neutral profiles must still produce a real score");
        assertEquals(Double.class, score.getClass());
        double v = (Double) score;
        assertEquals(true, v >= -1.0 && v <= 1.0, "centred cosine must stay in [-1, 1]");
    }
}
