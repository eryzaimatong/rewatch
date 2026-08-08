package com.rewatch.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rewatch.model.User;
import com.rewatch.repository.BlockRepository;
import com.rewatch.repository.FollowRepository;
import com.rewatch.repository.RatingRepository;
import com.rewatch.repository.TitleRepository;
import com.rewatch.repository.UserRepository;
import com.rewatch.repository.WatchlistFolderRepository;
import com.rewatch.repository.WatchlistItemRepository;

/**
 * Blocking's whole point is mutual hiding: A blocking B must be
 * indistinguishable, from B's side, from B never having a relationship with A
 * at all. These tests cover the two places that guarantee got wrong would
 * silently defeat the feature — isBlocked()'s symmetry, and follow()'s guard
 * against crossing an active block in either direction — without needing to
 * stub the profile/archetype machinery the read-side visibility methods also
 * touch.
 */
@ExtendWith(MockitoExtension.class)
class SocialServiceBlockTest {

    @Mock private UserRepository userRepo;
    @Mock private FollowRepository followRepo;
    @Mock private BlockRepository blockRepo;
    @Mock private RatingRepository ratingRepo;
    @Mock private TitleRepository titleRepo;
    @Mock private WatchlistFolderRepository folderRepo;
    @Mock private WatchlistItemRepository itemRepo;

    // ProfileService/ArchetypeService are concrete classes, not interfaces — Mockito's
    // inline mock maker needs bytecode retransformation to mock those, which isn't
    // supported on this JDK yet (see the other tests in this module, which only ever
    // mock repository interfaces for the same reason). None of the methods under test
    // here (isBlocked/follow/block/unblock) touch either dependency, so null is fine.
    private SocialService newService() {
        return new SocialService(userRepo, followRepo, blockRepo, ratingRepo, titleRepo,
                folderRepo, itemRepo, null, null, null);
    }

    @Test
    void isBlockedIsMutualRegardlessOfWhichSideBlocked() {
        when(blockRepo.existsByBlockerIdAndBlockedId(1L, 2L)).thenReturn(true);
        when(blockRepo.existsByBlockerIdAndBlockedId(2L, 1L)).thenReturn(false);

        SocialService svc = newService();

        assertTrue(svc.isBlocked(1L, 2L), "the blocker's own direction must read as blocked");
        assertTrue(svc.isBlocked(2L, 1L), "the blocked party's side must also read as blocked — this is the mutual-hiding guarantee");
    }

    @Test
    void isBlockedIsFalseWhenNeitherSideHasBlocked() {
        when(blockRepo.existsByBlockerIdAndBlockedId(1L, 2L)).thenReturn(false);
        when(blockRepo.existsByBlockerIdAndBlockedId(2L, 1L)).thenReturn(false);

        assertFalse(newService().isBlocked(1L, 2L));
    }

    @Test
    void followThrowsWhenTheCallerHasBlockedTheTarget() {
        when(userRepo.findById(2L)).thenReturn(Optional.of(new User()));
        when(blockRepo.existsByBlockerIdAndBlockedId(1L, 2L)).thenReturn(true);

        SocialService svc = newService();
        assertThrows(IllegalArgumentException.class, () -> svc.follow(1L, 2L));
        verify(followRepo, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void followThrowsWhenTheTargetHasBlockedTheCaller() {
        when(userRepo.findById(2L)).thenReturn(Optional.of(new User()));
        when(blockRepo.existsByBlockerIdAndBlockedId(1L, 2L)).thenReturn(false);
        when(blockRepo.existsByBlockerIdAndBlockedId(2L, 1L)).thenReturn(true);

        SocialService svc = newService();
        assertThrows(IllegalArgumentException.class, () -> svc.follow(1L, 2L));
        verify(followRepo, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void followSucceedsWhenNoBlockExistsEitherWay() {
        when(userRepo.findById(2L)).thenReturn(Optional.of(new User()));
        when(blockRepo.existsByBlockerIdAndBlockedId(1L, 2L)).thenReturn(false);
        when(blockRepo.existsByBlockerIdAndBlockedId(2L, 1L)).thenReturn(false);
        when(followRepo.existsByFollowerIdAndFolloweeId(1L, 2L)).thenReturn(false);

        newService().follow(1L, 2L);

        verify(followRepo, times(1)).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void blockingSomeoneClearsAnyExistingFollowEdgeInBothDirections() {
        when(userRepo.findById(2L)).thenReturn(Optional.of(new User()));
        when(blockRepo.existsByBlockerIdAndBlockedId(1L, 2L)).thenReturn(false);

        newService().block(1L, 2L);

        verify(followRepo).deleteByFollowerIdAndFolloweeId(1L, 2L);
        verify(followRepo).deleteByFollowerIdAndFolloweeId(2L, 1L);
        verify(blockRepo).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void blockingIsIdempotentAndDoesNotDuplicateTheRow() {
        when(userRepo.findById(2L)).thenReturn(Optional.of(new User()));
        when(blockRepo.existsByBlockerIdAndBlockedId(1L, 2L)).thenReturn(true);

        newService().block(1L, 2L);

        verify(blockRepo, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void cannotBlockSelf() {
        SocialService svc = newService();
        assertThrows(IllegalArgumentException.class, () -> svc.block(1L, 1L));
    }

    @Test
    void unblockOnlyRemovesTheBlockersOwnDirection() {
        newService().unblock(1L, 2L);
        verify(blockRepo).deleteByBlockerIdAndBlockedId(eq(1L), eq(2L));
        verify(blockRepo, never()).deleteByBlockerIdAndBlockedId(eq(2L), eq(1L));
    }
}
