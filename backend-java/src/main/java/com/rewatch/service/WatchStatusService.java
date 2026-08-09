package com.rewatch.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rewatch.model.WatchStatus;
import com.rewatch.repository.RatingRepository;
import com.rewatch.repository.WatchStatusRepository;

/**
 * "Currently Watching" / "Dropped" only — see WatchStatus's javadoc for why
 * "Watched" and "Plan to watch" aren't stored here at all.
 */
@Service
public class WatchStatusService {

    private static final Set<String> VALID_STATUSES = Set.of("WATCHING", "DROPPED");

    private final WatchStatusRepository watchStatusRepo;
    private final RatingRepository ratingRepo;

    public WatchStatusService(WatchStatusRepository watchStatusRepo, RatingRepository ratingRepo) {
        this.watchStatusRepo = watchStatusRepo;
        this.ratingRepo = ratingRepo;
    }

    @Transactional
    public void setStatus(Long userId, Long titleId, String status) {
        if (status == null || status.isBlank()) {
            clearStatus(userId, titleId);
            return;
        }
        String normalized = status.trim().toUpperCase();
        if (!VALID_STATUSES.contains(normalized)) {
            throw new IllegalArgumentException("Unknown watch status: " + status);
        }
        WatchStatus row = watchStatusRepo.findByUserIdAndTitleId(userId, titleId).orElseGet(WatchStatus::new);
        row.setUserId(userId);
        row.setTitleId(titleId);
        row.setStatus(normalized);
        row.setUpdatedAt(Instant.now());
        watchStatusRepo.save(row);
    }

    @Transactional
    public void clearStatus(Long userId, Long titleId) {
        watchStatusRepo.deleteByUserIdAndTitleId(userId, titleId);
    }

    /**
     * Merges explicit WatchStatus rows with a derived "WATCHED" for any title
     * that has a Rating but no explicit status row — an explicit status always
     * wins (e.g. a user can mark a title "Dropped" even after once rating it,
     * on a rewatch attempt that didn't go anywhere).
     */
    public Map<Long, String> statusesFor(Long userId) {
        Map<Long, String> statuses = new HashMap<>();
        watchStatusRepo.findByUserId(userId).forEach(ws -> statuses.put(ws.getTitleId(), ws.getStatus()));
        ratingRepo.findDistinctTitleIdsByUserId(userId).forEach(titleId ->
                statuses.putIfAbsent(titleId, "WATCHED"));
        return statuses;
    }
}
