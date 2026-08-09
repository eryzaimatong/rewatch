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

    private WatchlistService newService() {
        return new WatchlistService(itemRepo, folderRepo, titleRepo, null, null, null);
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
}
