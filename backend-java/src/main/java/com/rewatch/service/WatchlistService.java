package com.rewatch.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rewatch.dto.MovieDTO;
import com.rewatch.dto.WatchlistItemDTO;
import com.rewatch.dto.WatchlistRequests.AddItem;
import com.rewatch.model.Title;
import com.rewatch.model.WatchlistFolder;
import com.rewatch.model.WatchlistItem;
import com.rewatch.repository.CollectionFollowRepository;
import com.rewatch.repository.TitleRepository;
import com.rewatch.repository.WatchlistFolderRepository;
import com.rewatch.repository.WatchlistItemRepository;

/**
 * Real, persisted saves. Replaces the previous "watchlist", which was two
 * different kinds of fake: Dashboard.jsx faked its contents from the first
 * four catalog titles, and MovieFeed.jsx's save toggle only flipped local
 * React state that vanished on refresh — nothing was ever written anywhere.
 */
@Service
public class WatchlistService {

    private final WatchlistItemRepository itemRepo;
    private final WatchlistFolderRepository folderRepo;
    private final TitleRepository titleRepo;
    private final TmdbClient tmdb;
    private final EnrichmentService enrichmentService;
    private final Recommender recommender;
    private final CollectionFollowRepository collectionFollowRepo;

    public WatchlistService(WatchlistItemRepository itemRepo, WatchlistFolderRepository folderRepo,
                            TitleRepository titleRepo, TmdbClient tmdb,
                            EnrichmentService enrichmentService, Recommender recommender,
                            CollectionFollowRepository collectionFollowRepo) {
        this.itemRepo = itemRepo;
        this.folderRepo = folderRepo;
        this.titleRepo = titleRepo;
        this.tmdb = tmdb;
        this.enrichmentService = enrichmentService;
        this.recommender = recommender;
        this.collectionFollowRepo = collectionFollowRepo;
    }

    public List<WatchlistItemDTO> listItems(Long userId) {
        List<WatchlistItem> items = itemRepo.findByUserIdOrderByAddedAtDesc(userId);
        if (items.isEmpty()) {
            return List.of();
        }

        List<Long> titleIds = items.stream().map(WatchlistItem::getTitleId).toList();
        Map<Long, Title> titles = new HashMap<>();
        titleRepo.findAllById(titleIds).forEach(t -> titles.put(t.getId(), t));

        Map<Long, String> folderNames = new HashMap<>();
        folderRepo.findByUserIdOrderByNameAsc(userId).forEach(f -> folderNames.put(f.getId(), f.getName()));

        return items.stream()
                .map(item -> toDto(item, titles.get(item.getTitleId()), folderNames, userId))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    public List<WatchlistFolder> listFolders(Long userId) {
        return folderRepo.findByUserIdOrderByNameAsc(userId);
    }

    @Transactional
    public WatchlistFolder createFolder(Long userId, String name) {
        String trimmed = name.trim();
        return folderRepo.findByUserIdAndName(userId, trimmed)
                .orElseGet(() -> folderRepo.save(new WatchlistFolder(userId, trimmed, Instant.now())));
    }

    @Transactional
    public WatchlistItemDTO addItem(AddItem req) {
        Title title = resolveTitle(req);

        if (req.getFolderId() != null && !canAddTo(req.getFolderId(), req.getUserId())) {
            throw new IllegalArgumentException("You don't have permission to add to that list.");
        }

        WatchlistItem item = itemRepo.findByUserIdAndTitleId(req.getUserId(), title.getId())
                .orElseGet(() -> new WatchlistItem(req.getUserId(), title.getId(), req.getFolderId(), Instant.now()));

        if (req.getFolderId() != null) {
            item.setFolderId(req.getFolderId());
        }
        item = itemRepo.save(item);

        Map<Long, String> folderNames = new HashMap<>();
        folderRepo.findByUserIdOrderByNameAsc(req.getUserId()).forEach(f -> folderNames.put(f.getId(), f.getName()));
        return toDto(item, title, folderNames, req.getUserId());
    }

    @Transactional
    public boolean removeItem(Long userId, Long itemId) {
        return itemRepo.findByIdAndUserId(itemId, userId).map(item -> {
            itemRepo.delete(item);
            return true;
        }).orElse(false);
    }

    /**
     * @throws IllegalArgumentException if the trimmed name collides with a
     *         DIFFERENT folder the same user already has — silently merging
     *         two folders' contents under one name would be a much bigger,
     *         surprising behavior change for what looks like a simple rename.
     */
    @Transactional
    public WatchlistFolder renameFolder(Long userId, Long folderId, String name) {
        WatchlistFolder folder = folderRepo.findById(folderId)
                .filter(f -> f.getUserId().equals(userId))
                .orElse(null);
        if (folder == null) {
            return null;
        }
        String trimmed = name.trim();
        folderRepo.findByUserIdAndName(userId, trimmed).ifPresent(existing -> {
            if (!existing.getId().equals(folderId)) {
                throw new IllegalArgumentException("You already have a list named \"" + trimmed + "\".");
            }
        });
        folder.setName(trimmed);
        return folderRepo.save(folder);
    }

    /**
     * Deleting a folder only ungroups its items back to "no folder" — it never
     * deletes the underlying watchlist entries. A shelf is an organizational
     * label on titles someone already chose to save; losing the label isn't
     * grounds to lose the save itself. Collection-follow edges pointing at it
     * are cleaned up explicitly (rather than left as harmless orphans, the
     * pattern used elsewhere) since there's a real repository method for it.
     */
    @Transactional
    public boolean deleteFolder(Long userId, Long folderId) {
        WatchlistFolder folder = folderRepo.findById(folderId)
                .filter(f -> f.getUserId().equals(userId))
                .orElse(null);
        if (folder == null) {
            return false;
        }
        List<WatchlistItem> items = itemRepo.findByFolderIdOrderByAddedAtDesc(folderId);
        items.forEach(item -> item.setFolderId(null));
        itemRepo.saveAll(items);
        collectionFollowRepo.deleteByFolderId(folderId);
        folderRepo.delete(folder);
        return true;
    }

    /** Toggles whether a folder is shareable on the owner's public profile. */
    @Transactional
    public WatchlistFolder setFolderVisibility(Long userId, Long folderId, boolean isPublic) {
        return folderRepo.findById(folderId)
                .filter(f -> f.getUserId().equals(userId))
                .map(f -> {
                    f.setPublic(isPublic);
                    return folderRepo.save(f);
                })
                .orElse(null);
    }

    /**
     * Owner-only toggle. Collaborative only means anything alongside public —
     * there's no invite-link/private-sharing mechanism in this app, so a
     * private collaborative folder would be reachable by nobody but its owner,
     * who doesn't need permission to add to their own list.
     */
    @Transactional
    public WatchlistFolder setFolderCollaborative(Long userId, Long folderId, boolean collaborative) {
        WatchlistFolder folder = folderRepo.findById(folderId)
                .filter(f -> f.getUserId().equals(userId))
                .orElse(null);
        if (folder == null) {
            return null;
        }
        if (collaborative && !folder.isPublic()) {
            throw new IllegalArgumentException("Make the list public before turning on collaboration.");
        }
        folder.setCollaborative(collaborative);
        return folderRepo.save(folder);
    }

    /**
     * True if userId may add/move an item into this folder — its own, or a
     * public+collaborative one. Package-private for direct testing.
     */
    boolean canAddTo(Long folderId, Long userId) {
        WatchlistFolder folder = folderRepo.findById(folderId).orElse(null);
        if (folder == null) {
            return false;
        }
        if (folder.getUserId().equals(userId)) {
            return true;
        }
        return folder.isPublic() && folder.isCollaborative();
    }

    @Transactional
    public WatchlistItemDTO moveItem(Long userId, Long itemId, Long folderId) {
        WatchlistItem item = itemRepo.findByIdAndUserId(itemId, userId).orElse(null);
        if (item == null) {
            return null;
        }
        if (folderId != null && !canAddTo(folderId, userId)) {
            return null;
        }
        item.setFolderId(folderId);
        item = itemRepo.save(item);

        Title title = titleRepo.findById(item.getTitleId()).orElse(null);
        Map<Long, String> folderNames = new HashMap<>();
        folderRepo.findByUserIdOrderByNameAsc(userId).forEach(f -> folderNames.put(f.getId(), f.getName()));
        return toDto(item, title, folderNames, userId);
    }

    private Title resolveTitle(AddItem req) {
        if (req.getTitleId() != null) {
            return titleRepo.findById(req.getTitleId())
                    .orElseThrow(() -> new IllegalArgumentException("Unknown titleId " + req.getTitleId()));
        }
        if (req.getTmdbId() == null) {
            throw new IllegalArgumentException("Either titleId or tmdbId is required");
        }
        return titleRepo.findByTmdbId(req.getTmdbId()).orElseGet(() -> {
            Title created = new Title();
            created.setTmdbId(req.getTmdbId());
            created.setTitle(req.getTitle() != null ? req.getTitle() : "Untitled");
            Title saved = titleRepo.save(created);
            if (tmdb.isConfigured()) {
                enrichmentService.enrichOne(saved);
            }
            return saved;
        });
    }

    private WatchlistItemDTO toDto(WatchlistItem item, Title title, Map<Long, String> folderNames, Long userId) {
        if (title == null) {
            return null;
        }
        MovieDTO scored = recommender.scoreForUser(title, userId);

        WatchlistItemDTO dto = new WatchlistItemDTO();
        dto.setId(item.getId());
        dto.setTitleId(title.getId());
        dto.setTmdbId(title.getTmdbId());
        dto.setTitle(title.getTitle());
        dto.setPoster(title.getPoster());
        dto.setSynopsis(title.getSynopsis());
        dto.setYear(title.getYear());
        dto.setMatchScore(scored.getMatchScore());
        dto.setFolderId(item.getFolderId());
        // folderNames is normally built from the caller's OWN folders — falls
        // back to a direct lookup for a folder the caller CONTRIBUTED to but
        // doesn't own (a collaborative folder), so their own watchlist view
        // still shows the real name instead of blank.
        String folderName = item.getFolderId() == null ? null : folderNames.get(item.getFolderId());
        if (folderName == null && item.getFolderId() != null) {
            folderName = folderRepo.findById(item.getFolderId()).map(WatchlistFolder::getName).orElse(null);
        }
        dto.setFolderName(folderName);
        dto.setAddedAt(item.getAddedAt());
        return dto;
    }
}
