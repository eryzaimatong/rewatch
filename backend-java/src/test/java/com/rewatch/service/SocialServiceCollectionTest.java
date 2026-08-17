package com.rewatch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rewatch.model.User;
import com.rewatch.model.WatchlistFolder;
import com.rewatch.model.WatchlistItem;
import com.rewatch.repository.BlockRepository;
import com.rewatch.repository.CollectionFollowRepository;
import com.rewatch.repository.FollowRepository;
import com.rewatch.repository.RatingRepository;
import com.rewatch.repository.TitleRepository;
import com.rewatch.repository.UserRepository;
import com.rewatch.repository.WatchlistFolderRepository;
import com.rewatch.repository.WatchlistItemRepository;

/**
 * "Collections" is the public-facing name for a followable WatchlistFolder —
 * these tests cover the guards unique to that (can't follow your own, can't
 * follow a private one, blocked users are invisible to each other) since the
 * underlying follow-edge mechanics are already proven by
 * SocialServiceBlockTest for the user-to-user case.
 */
@ExtendWith(MockitoExtension.class)
class SocialServiceCollectionTest {

    @Mock private UserRepository userRepo;
    @Mock private FollowRepository followRepo;
    @Mock private BlockRepository blockRepo;
    @Mock private RatingRepository ratingRepo;
    @Mock private TitleRepository titleRepo;
    @Mock private WatchlistFolderRepository folderRepo;
    @Mock private WatchlistItemRepository itemRepo;
    @Mock private CollectionFollowRepository collectionFollowRepo;

    private SocialService newService() {
        return new SocialService(userRepo, followRepo, blockRepo, ratingRepo, titleRepo,
                folderRepo, itemRepo, null, null, collectionFollowRepo);
    }

    private WatchlistFolder folder(long id, long ownerId, boolean isPublic) {
        WatchlistFolder f = new WatchlistFolder(ownerId, "Comfort Rewatches", Instant.now());
        f.setId(id);
        f.setPublic(isPublic);
        return f;
    }

    @Test
    void followThrowsWhenTheCollectionIsPrivate() {
        when(folderRepo.findById(10L)).thenReturn(Optional.of(folder(10L, 2L, false)));

        assertThrows(IllegalArgumentException.class, () -> newService().followCollection(1L, 10L));
        verify(collectionFollowRepo, never()).save(any());
    }

    @Test
    void followThrowsWhenFollowingYourOwnCollection() {
        when(folderRepo.findById(10L)).thenReturn(Optional.of(folder(10L, 1L, true)));

        assertThrows(IllegalArgumentException.class, () -> newService().followCollection(1L, 10L));
        verify(collectionFollowRepo, never()).save(any());
    }

    @Test
    void followThrowsWhenEitherSideHasBlockedTheOther() {
        when(folderRepo.findById(10L)).thenReturn(Optional.of(folder(10L, 2L, true)));
        when(blockRepo.existsByBlockerIdAndBlockedId(1L, 2L)).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> newService().followCollection(1L, 10L));
        verify(collectionFollowRepo, never()).save(any());
    }

    @Test
    void followSucceedsAndIsIdempotent() {
        when(folderRepo.findById(10L)).thenReturn(Optional.of(folder(10L, 2L, true)));
        when(blockRepo.existsByBlockerIdAndBlockedId(anyLong(), anyLong())).thenReturn(false);
        when(collectionFollowRepo.existsByUserIdAndFolderId(1L, 10L)).thenReturn(false, true);

        SocialService svc = newService();
        svc.followCollection(1L, 10L);
        svc.followCollection(1L, 10L);

        verify(collectionFollowRepo, times(1)).save(any());
    }

    @Test
    void unfollowDeletesTheEdge() {
        newService().unfollowCollection(1L, 10L);
        verify(collectionFollowRepo).deleteByUserIdAndFolderId(1L, 10L);
    }

    @Test
    void discoverExcludesTheCallersOwnFoldersAndBlockedOwners() {
        WatchlistFolder own = folder(1L, 1L, true);
        WatchlistFolder blockedOwners = folder(2L, 3L, true);
        WatchlistFolder visible = folder(3L, 4L, true);

        when(folderRepo.findByIsPublicTrueOrderByCreatedAtDesc()).thenReturn(List.of(own, blockedOwners, visible));
        // isBlocked's || short-circuits once the first direction is true, so
        // only (1,3) needs stubbing for the blocked-owner folder.
        when(blockRepo.existsByBlockerIdAndBlockedId(1L, 3L)).thenReturn(true);
        when(blockRepo.existsByBlockerIdAndBlockedId(1L, 4L)).thenReturn(false);
        when(blockRepo.existsByBlockerIdAndBlockedId(4L, 1L)).thenReturn(false);

        User owner4 = new User();
        owner4.setId(4L);
        owner4.setUsername("visible-owner");
        when(userRepo.findAllById(any())).thenReturn(List.of(owner4));
        // Non-empty on purpose: discoverCollections filters out folders with no
        // items (an empty public folder has nothing to discover), so an empty
        // fixture here would fail this test for the same reason a real empty
        // "Just Mine" default shelf no longer surfaces in production.
        WatchlistItem item = new WatchlistItem(4L, 100L, 3L, Instant.now());
        when(itemRepo.findByFolderIdOrderByAddedAtDesc(3L)).thenReturn(List.of(item));
        when(collectionFollowRepo.countByFolderId(3L)).thenReturn(0L);

        List<java.util.Map<String, Object>> results = newService().discoverCollections(1L, 20);

        assertEquals(1, results.size(), "only the third folder (not owned by caller, not blocked) should surface");
        assertEquals(3L, results.get(0).get("folderId"));
        assertEquals("visible-owner", results.get(0).get("ownerUsername"));
    }

    @Test
    void followedCollectionsSkipsAFolderThatWentPrivateOrWasDeleted() {
        com.rewatch.model.CollectionFollow edgeToPrivate = new com.rewatch.model.CollectionFollow(1L, 5L, Instant.now());
        com.rewatch.model.CollectionFollow edgeToDeleted = new com.rewatch.model.CollectionFollow(1L, 6L, Instant.now());
        when(collectionFollowRepo.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(edgeToPrivate, edgeToDeleted));

        WatchlistFolder nowPrivate = folder(5L, 2L, false);
        // folder 6 no longer exists — findAllById simply omits it
        when(folderRepo.findAllById(any())).thenReturn(List.of(nowPrivate));

        List<java.util.Map<String, Object>> results = newService().followedCollections(1L);

        assertTrue(results.isEmpty(), "a private or deleted followed collection must not render a broken card");
    }
}
