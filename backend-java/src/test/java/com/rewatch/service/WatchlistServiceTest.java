package com.rewatch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rewatch.model.WatchlistFolder;
import com.rewatch.repository.CollectionFollowRepository;
import com.rewatch.repository.TitleRepository;
import com.rewatch.repository.WatchlistFolderRepository;
import com.rewatch.repository.WatchlistItemRepository;

/**
 * Collaborative folders are the one place this app lets someone other than a
 * row's owner write into a shared space — canAddTo() and
 * setFolderCollaborative() are the entire permission boundary for that, so
 * both get direct coverage rather than only being exercised incidentally
 * through addItem()/moveItem() (which also need a real Recommender for
 * scoreForUser, unrelated to the permission logic itself).
 */
@ExtendWith(MockitoExtension.class)
class WatchlistServiceTest {

    @Mock private WatchlistItemRepository itemRepo;
    @Mock private WatchlistFolderRepository folderRepo;
    @Mock private TitleRepository titleRepo;
    @Mock private CollectionFollowRepository collectionFollowRepo;

    private WatchlistService newService() {
        return new WatchlistService(itemRepo, folderRepo, titleRepo, null, null, null, collectionFollowRepo);
    }

    private WatchlistFolder folder(Long ownerId, boolean isPublic, boolean collaborative) {
        WatchlistFolder f = new WatchlistFolder(ownerId, "Test Shelf", java.time.Instant.now());
        f.setId(99L);
        f.setPublic(isPublic);
        f.setCollaborative(collaborative);
        return f;
    }

    @Test
    void ownerCanAlwaysAddToTheirOwnFolder() {
        when(folderRepo.findById(99L)).thenReturn(Optional.of(folder(1L, false, false)));
        assertTrue(newService().canAddTo(99L, 1L));
    }

    @Test
    void otherUserCannotAddToAPrivateFolder() {
        when(folderRepo.findById(99L)).thenReturn(Optional.of(folder(1L, false, false)));
        assertFalse(newService().canAddTo(99L, 2L));
    }

    @Test
    void otherUserCannotAddToAPublicButNonCollaborativeFolder() {
        when(folderRepo.findById(99L)).thenReturn(Optional.of(folder(1L, true, false)));
        assertFalse(newService().canAddTo(99L, 2L));
    }

    @Test
    void otherUserCanAddToAPublicCollaborativeFolder() {
        when(folderRepo.findById(99L)).thenReturn(Optional.of(folder(1L, true, true)));
        assertTrue(newService().canAddTo(99L, 2L));
    }

    @Test
    void canAddToIsFalseForAnUnknownFolder() {
        when(folderRepo.findById(99L)).thenReturn(Optional.empty());
        assertFalse(newService().canAddTo(99L, 2L));
    }

    @Test
    void ownerCanTurnOnCollaborationOnAPublicFolder() {
        WatchlistFolder f = folder(1L, true, false);
        when(folderRepo.findById(99L)).thenReturn(Optional.of(f));
        when(folderRepo.save(f)).thenReturn(f);

        WatchlistFolder result = newService().setFolderCollaborative(1L, 99L, true);

        assertTrue(result.isCollaborative());
    }

    @Test
    void rejectsTurningOnCollaborationOnAPrivateFolder() {
        when(folderRepo.findById(99L)).thenReturn(Optional.of(folder(1L, false, false)));
        assertThrows(IllegalArgumentException.class, () -> newService().setFolderCollaborative(1L, 99L, true));
    }

    @Test
    void nonOwnerCannotToggleCollaboration() {
        when(folderRepo.findById(99L)).thenReturn(Optional.of(folder(1L, true, false)));
        assertNull(newService().setFolderCollaborative(2L, 99L, true));
    }

    @Test
    void turningOffCollaborationNeverThrowsRegardlessOfVisibility() {
        WatchlistFolder f = folder(1L, false, true);
        when(folderRepo.findById(99L)).thenReturn(Optional.of(f));
        when(folderRepo.save(f)).thenReturn(f);

        WatchlistFolder result = newService().setFolderCollaborative(1L, 99L, false);

        assertFalse(result.isCollaborative());
        assertEquals(99L, result.getId());
    }

    @Test
    void renameSucceedsWhenNoOtherFolderHasThatName() {
        WatchlistFolder f = folder(1L, false, false);
        when(folderRepo.findById(99L)).thenReturn(Optional.of(f));
        when(folderRepo.findByUserIdAndName(1L, "Cozy Rewatches")).thenReturn(Optional.empty());
        when(folderRepo.save(f)).thenReturn(f);

        WatchlistFolder result = newService().renameFolder(1L, 99L, "Cozy Rewatches");

        assertEquals("Cozy Rewatches", result.getName());
    }

    @Test
    void renameIsANoOpNameCollisionWithItself() {
        // findByUserIdAndName can legitimately return the SAME folder being
        // renamed (e.g. a client retry, or the name didn't actually change) —
        // that must not be treated as a collision with a different folder.
        WatchlistFolder f = folder(1L, false, false);
        when(folderRepo.findById(99L)).thenReturn(Optional.of(f));
        when(folderRepo.findByUserIdAndName(1L, "Test Shelf")).thenReturn(Optional.of(f));
        when(folderRepo.save(f)).thenReturn(f);

        WatchlistFolder result = newService().renameFolder(1L, 99L, "Test Shelf");

        assertEquals("Test Shelf", result.getName());
    }

    @Test
    void renameRejectsCollidingWithADifferentExistingFolder() {
        WatchlistFolder f = folder(1L, false, false);
        WatchlistFolder other = folder(1L, false, false);
        other.setId(100L);
        when(folderRepo.findById(99L)).thenReturn(Optional.of(f));
        when(folderRepo.findByUserIdAndName(1L, "Test Shelf")).thenReturn(Optional.of(other));

        assertThrows(IllegalArgumentException.class, () -> newService().renameFolder(1L, 99L, "Test Shelf"));
    }

    @Test
    void nonOwnerCannotRename() {
        when(folderRepo.findById(99L)).thenReturn(Optional.of(folder(1L, false, false)));
        assertNull(newService().renameFolder(2L, 99L, "Hijacked"));
    }

    @Test
    void deleteFolderUngroupsItemsRatherThanDeletingThem() {
        WatchlistFolder f = folder(1L, false, false);
        when(folderRepo.findById(99L)).thenReturn(Optional.of(f));

        com.rewatch.model.WatchlistItem item = new com.rewatch.model.WatchlistItem(1L, 5L, 99L, java.time.Instant.now());
        when(itemRepo.findByFolderIdOrderByAddedAtDesc(99L)).thenReturn(java.util.List.of(item));

        boolean result = newService().deleteFolder(1L, 99L);

        assertTrue(result);
        assertNull(item.getFolderId());
        org.mockito.Mockito.verify(itemRepo).saveAll(java.util.List.of(item));
        org.mockito.Mockito.verify(collectionFollowRepo).deleteByFolderId(99L);
        org.mockito.Mockito.verify(folderRepo).delete(f);
    }

    @Test
    void nonOwnerCannotDelete() {
        when(folderRepo.findById(99L)).thenReturn(Optional.of(folder(1L, false, false)));
        assertFalse(newService().deleteFolder(2L, 99L));
        org.mockito.Mockito.verify(folderRepo, org.mockito.Mockito.never()).delete(org.mockito.ArgumentMatchers.any());
    }
}
