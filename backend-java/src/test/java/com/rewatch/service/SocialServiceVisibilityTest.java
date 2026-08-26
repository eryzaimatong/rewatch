package com.rewatch.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import com.rewatch.model.User;
import com.rewatch.repository.BlockRepository;
import com.rewatch.repository.FollowRepository;
import com.rewatch.repository.RatingRepository;
import com.rewatch.repository.TitleRepository;
import com.rewatch.repository.UserRepository;
import com.rewatch.repository.WatchlistFolderRepository;
import com.rewatch.repository.WatchlistItemRepository;

/**
 * The IDOR finding this closes: followers(), following(), and publicLists()
 * had no visibility check at all, unlike their siblings publicProfile() and
 * reviews() — a private profile's connections and "public" folders were
 * readable by any authenticated user who knew the target's id. These tests
 * prove the repository query itself never runs when it shouldn't (verify,
 * not just an empty return value — an empty result and a blocked result look
 * identical from the return value alone), and that self-access and public
 * profiles are unaffected.
 *
 * profileService/archetypeService are concrete classes this JDK's Mockito
 * can't mock (see SocialServiceBlockTest) — the "allowed" assertions here
 * rely on an empty id list short-circuiting summarize() before it ever
 * touches either, same technique as that file.
 */
@ExtendWith(MockitoExtension.class)
class SocialServiceVisibilityTest {

    @Mock private UserRepository userRepo;
    @Mock private FollowRepository followRepo;
    @Mock private BlockRepository blockRepo;
    @Mock private RatingRepository ratingRepo;
    @Mock private TitleRepository titleRepo;
    @Mock private WatchlistFolderRepository folderRepo;
    @Mock private WatchlistItemRepository itemRepo;

    private SocialService newService() {
        return new SocialService(userRepo, followRepo, blockRepo, ratingRepo, titleRepo,
                folderRepo, itemRepo, null, null, null);
    }

    private User privateUser() {
        User u = new User();
        u.setId(2L);
        u.setProfilePublic(false);
        return u;
    }

    private User publicUser() {
        User u = new User();
        u.setId(2L);
        u.setProfilePublic(true);
        return u;
    }

    // ---------- followers ----------

    @Test
    void followersNeverQueriesWhenTheTargetIsPrivateAndCallerIsNotTheOwner() {
        when(userRepo.findById(2L)).thenReturn(Optional.of(privateUser()));

        assertTrue(newService().followers(2L, 1L).isEmpty());

        verify(followRepo, never()).findByFolloweeIdOrderByCreatedAtDesc(any(), any(Pageable.class));
    }

    @Test
    void followersQueriesNormallyWhenTheTargetIsPublic() {
        when(userRepo.findById(2L)).thenReturn(Optional.of(publicUser()));

        newService().followers(2L, 1L);

        verify(followRepo, times(1)).findByFolloweeIdOrderByCreatedAtDesc(any(), any(Pageable.class));
    }

    @Test
    void followersQueriesNormallyWhenTheCallerIsTheOwnerEvenIfPrivate() {
        newService().followers(2L, 2L);

        verify(followRepo, times(1)).findByFolloweeIdOrderByCreatedAtDesc(any(), any(Pageable.class));
        verify(userRepo, never()).findById(any());
    }

    @Test
    void followersReturnsEmptyWhenTheCallerIsBlockedByTheTarget() {
        // isBlocked(2L, 1L) short-circuits on the first true — the reverse
        // direction is never queried, so it isn't stubbed here.
        when(blockRepo.existsByBlockerIdAndBlockedId(2L, 1L)).thenReturn(true);

        assertTrue(newService().followers(2L, 1L).isEmpty());

        verify(followRepo, never()).findByFolloweeIdOrderByCreatedAtDesc(any(), any(Pageable.class));
    }

    // ---------- following ----------

    @Test
    void followingNeverQueriesWhenTheTargetIsPrivateAndCallerIsNotTheOwner() {
        when(userRepo.findById(2L)).thenReturn(Optional.of(privateUser()));

        assertTrue(newService().following(2L, 1L).isEmpty());

        verify(followRepo, never()).findByFollowerIdOrderByCreatedAtDesc(any(), any(Pageable.class));
    }

    @Test
    void followingQueriesNormallyWhenTheTargetIsPublic() {
        when(userRepo.findById(2L)).thenReturn(Optional.of(publicUser()));

        newService().following(2L, 1L);

        verify(followRepo, times(1)).findByFollowerIdOrderByCreatedAtDesc(any(), any(Pageable.class));
    }

    // ---------- publicLists ----------

    @Test
    void publicListsNeverQueriesWhenTheTargetIsPrivateAndCallerIsNotTheOwner() {
        when(userRepo.findById(2L)).thenReturn(Optional.of(privateUser()));

        assertTrue(newService().publicLists(2L, 1L).isEmpty());

        verify(folderRepo, never()).findByUserIdOrderByNameAsc(any());
    }

    @Test
    void publicListsQueriesNormallyWhenTheTargetIsPublic() {
        when(userRepo.findById(2L)).thenReturn(Optional.of(publicUser()));

        newService().publicLists(2L, 1L);

        verify(folderRepo, times(1)).findByUserIdOrderByNameAsc(2L);
    }

    @Test
    void publicListsQueriesNormallyWhenTheCallerIsTheOwnerEvenIfPrivate() {
        newService().publicLists(2L, 2L);

        verify(folderRepo, times(1)).findByUserIdOrderByNameAsc(2L);
        verify(userRepo, never()).findById(any());
    }
}
