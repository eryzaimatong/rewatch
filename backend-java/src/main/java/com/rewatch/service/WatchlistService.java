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

    public WatchlistService(WatchlistItemRepository itemRepo, WatchlistFolderRepository folderRepo,
                            TitleRepository titleRepo, TmdbClient tmdb,
                            EnrichmentService enrichmentService, Recommender recommender) {
        this.itemRepo = itemRepo;
        this.folderRepo = folderRepo;
        this.titleRepo = titleRepo;
        this.tmdb = tmdb;
        this.enrichmentService = enrichmentService;
        this.recommender = recommender;
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

    @Transactional
    public WatchlistItemDTO moveItem(Long userId, Long itemId, Long folderId) {
        WatchlistItem item = itemRepo.findByIdAndUserId(itemId, userId).orElse(null);
        if (item == null) {
            return null;
        }
        if (folderId != null && !folderRepo.existsByIdAndUserId(folderId, userId)) {
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
        dto.setFolderName(item.getFolderId() == null ? null : folderNames.get(item.getFolderId()));
        dto.setAddedAt(item.getAddedAt());
        return dto;
    }
}
