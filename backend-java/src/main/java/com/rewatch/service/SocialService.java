package com.rewatch.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rewatch.dto.UserSummaryDTO;
import com.rewatch.model.Block;
import com.rewatch.model.CollectionFollow;
import com.rewatch.model.Follow;
import com.rewatch.model.Rating;
import com.rewatch.model.Title;
import com.rewatch.model.Trait;
import com.rewatch.model.TraitNode;
import com.rewatch.model.TraitVector;
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
 * The social layer, gated on real auth (Phase 9) because every one of these
 * endpoints either reads a specific user's data across an account boundary
 * (public profile, reviews, lists) or writes an edge on the caller's own
 * behalf (follow/unfollow) — both need a trustworthy caller identity, which is
 * exactly what didn't exist before JWT auth landed.
 *
 * The differentiator over a plain follow/review app: {@link #dnaMatches} is a
 * real signal computed from the same TraitVector machinery that powers
 * recommendations, not a popularity or mutual-follow heuristic.
 */
@Service
public class SocialService {

    private final UserRepository userRepo;
    private final FollowRepository followRepo;
    private final BlockRepository blockRepo;
    private final RatingRepository ratingRepo;
    private final TitleRepository titleRepo;
    private final WatchlistFolderRepository folderRepo;
    private final WatchlistItemRepository itemRepo;
    private final ProfileService profileService;
    private final ArchetypeService archetypeService;
    private final CollectionFollowRepository collectionFollowRepo;

    public SocialService(UserRepository userRepo, FollowRepository followRepo, BlockRepository blockRepo,
                         RatingRepository ratingRepo, TitleRepository titleRepo,
                         WatchlistFolderRepository folderRepo, WatchlistItemRepository itemRepo,
                         ProfileService profileService, ArchetypeService archetypeService,
                         CollectionFollowRepository collectionFollowRepo) {
        this.userRepo = userRepo;
        this.followRepo = followRepo;
        this.blockRepo = blockRepo;
        this.ratingRepo = ratingRepo;
        this.titleRepo = titleRepo;
        this.folderRepo = folderRepo;
        this.itemRepo = itemRepo;
        this.profileService = profileService;
        this.archetypeService = archetypeService;
        this.collectionFollowRepo = collectionFollowRepo;
    }

    /** Mutual: true if either side has blocked the other. Every visibility check below relies on this. */
    public boolean isBlocked(Long a, Long b) {
        return blockRepo.existsByBlockerIdAndBlockedId(a, b) || blockRepo.existsByBlockerIdAndBlockedId(b, a);
    }

    /** A private profile is visible only to its own owner. Unknown user -> not visible. */
    private boolean isProfileVisibleTo(Long targetId, Long callerId) {
        if (callerId != null && callerId.equals(targetId)) {
            return true;
        }
        User target = userRepo.findById(targetId).orElse(null);
        return target != null && target.isProfilePublic();
    }

    @Transactional
    public Map<String, Object> block(Long blockerId, Long blockedId) {
        if (blockerId.equals(blockedId)) {
            throw new IllegalArgumentException("Cannot block yourself");
        }
        if (userRepo.findById(blockedId).isEmpty()) {
            throw new IllegalArgumentException("Unknown user " + blockedId);
        }
        if (!blockRepo.existsByBlockerIdAndBlockedId(blockerId, blockedId)) {
            blockRepo.save(new Block(blockerId, blockedId, Instant.now()));
        }
        // A block supersedes any existing follow relationship in either direction.
        followRepo.deleteByFollowerIdAndFolloweeId(blockerId, blockedId);
        followRepo.deleteByFollowerIdAndFolloweeId(blockedId, blockerId);
        return Map.of("status", "success", "blocked", true);
    }

    @Transactional
    public Map<String, Object> unblock(Long blockerId, Long blockedId) {
        blockRepo.deleteByBlockerIdAndBlockedId(blockerId, blockedId);
        return Map.of("status", "success", "blocked", false);
    }

    public List<UserSummaryDTO> listBlocked(Long blockerId) {
        List<Long> ids = blockRepo.findByBlockerIdOrderByCreatedAtDesc(blockerId).stream()
                .map(Block::getBlockedId).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, User> users = new HashMap<>();
        userRepo.findAllById(ids).forEach(u -> users.put(u.getId(), u));
        return ids.stream()
                .map(users::get)
                .filter(java.util.Objects::nonNull)
                .map(u -> withAvatar(new UserSummaryDTO(u.getId(), u.getUsername(), null, false), u))
                .collect(Collectors.toList());
    }

    private static UserSummaryDTO withAvatar(UserSummaryDTO dto, User u) {
        dto.setAvatarUrl(u.getAvatarUrl());
        return dto;
    }

    public Map<String, Object> publicProfile(Long targetId, Long callerId) {
        User user = userRepo.findById(targetId).orElse(null);
        if (user == null) {
            return null;
        }
        // A blocked profile 404s exactly like a nonexistent one (see
        // SocialController.profile()) rather than a distinguishable 403 — that
        // would let a blocked user fingerprint "this profile exists, I'm just
        // blocked," a mild info leak most block features avoid.
        if (callerId != null && isBlocked(targetId, callerId)) {
            return null;
        }
        // Same 404-not-403 shape as blocking above — a private profile is
        // indistinguishable from a nonexistent one to anyone but its owner.
        if (!isProfileVisibleTo(targetId, callerId)) {
            return null;
        }

        Map<Trait, TraitNode> profile = profileService.currentProfile(targetId);
        var archetype = archetypeService.classify(profile);

        Map<String, Object> traits = new LinkedHashMap<>();
        for (Trait t : Trait.values()) {
            TraitNode n = profile.get(t);
            traits.put(t.key(), Map.of("val", n.getVal(), "label", t.label()));
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("userId", user.getId());
        res.put("username", user.getUsername());
        res.put("avatarUrl", user.getAvatarUrl());
        res.put("avatarFrame", user.getAvatarFrame());
        res.put("nickname", user.getNickname());
        res.put("bio", user.getBio());
        res.put("profileSong", user.getProfileSong());
        res.put("profileTheme", user.getProfileTheme() == null ? "cinema" : user.getProfileTheme());
        res.put("archetype", archetype.archetype());
        res.put("archetypeBlurb", archetype.blurb());
        res.put("ratingCount", ratingRepo.countByUserId(targetId));
        res.put("followerCount", followRepo.countByFolloweeId(targetId));
        res.put("followingCount", followRepo.countByFollowerId(targetId));
        res.put("isFollowing", callerId != null && followRepo.existsByFollowerIdAndFolloweeId(callerId, targetId));
        res.put("isSelf", callerId != null && callerId.equals(targetId));
        res.put("compatibilityScore", compatibilityScore(targetId, callerId));
        res.put("compatibilityBreakdown", compatibilityBreakdown(targetId, callerId));
        res.put("traits", traits);
        res.put("pinnedTitles", pinnedTitles(user));
        res.put("pinnedReview", pinnedReview(user));
        res.put("pinnedCollection", pinnedCollection(user, callerId));
        return res;
    }

    /** Resolves User.pinnedTitleIds into real title cards — never fabricated, just whatever's still in the catalog. */
    private List<Map<String, Object>> pinnedTitles(User user) {
        if (user.getPinnedTitleIds() == null || user.getPinnedTitleIds().isBlank()) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (String idStr : user.getPinnedTitleIds().split(",")) {
            try {
                Title t = titleRepo.findById(Long.parseLong(idStr.trim())).orElse(null);
                if (t != null) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("titleId", t.getId());
                    m.put("tmdbId", t.getTmdbId());
                    m.put("title", t.getTitle());
                    m.put("poster", t.getPoster());
                    out.add(m);
                }
            } catch (NumberFormatException ignored) {
                // A malformed stored value shouldn't break the whole profile — just skip it.
            }
        }
        return out;
    }

    private Map<String, Object> pinnedReview(User user) {
        if (user.getPinnedRatingId() == null) {
            return null;
        }
        Rating r = ratingRepo.findById(user.getPinnedRatingId()).orElse(null);
        if (r == null || r.getMoment() == null || r.getMoment().isBlank()) {
            return null;
        }
        Title t = titleRepo.findById(r.getTitleId()).orElse(null);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ratingId", r.getId());
        m.put("titleId", r.getTitleId());
        m.put("title", t == null ? "Unknown title" : t.getTitle());
        m.put("poster", t == null ? null : t.getPoster());
        m.put("overall", r.getOverall());
        m.put("moment", r.getMoment());
        return m;
    }

    private Map<String, Object> pinnedCollection(User user, Long callerId) {
        if (user.getPinnedFolderId() == null) {
            return null;
        }
        WatchlistFolder folder = folderRepo.findById(user.getPinnedFolderId()).orElse(null);
        if (folder == null) {
            return null;
        }
        return collectionSummary(folder, user, callerId);
    }

    /**
     * Same centred-cosine metric dnaMatches ranks by, computed for exactly the
     * pair of users viewing/being viewed — dnaMatches never surfaced this on an
     * individual's own profile page, only in the ranked match list. Null (not
     * 0) when it isn't meaningful: no caller, viewing your own profile, or
     * either side under the 3-rating floor that makes the comparison noise.
     */
    private Double compatibilityScore(Long targetId, Long callerId) {
        if (callerId == null || callerId.equals(targetId)) {
            return null;
        }
        if (ratingRepo.countByUserId(callerId) < 3 || ratingRepo.countByUserId(targetId) < 3) {
            return null;
        }
        TraitVector callerVec = profileService.vectorOf(profileService.currentProfile(callerId));
        TraitVector targetVec = profileService.vectorOf(profileService.currentProfile(targetId));
        return callerVec.centredCosine(targetVec);
    }

    /**
     * The single % score answers "how compatible," not "compatible how" — this
     * answers the second question with real per-axis comparisons, never an
     * invented one: "shared" is an axis where both of you sit strongly on the
     * same side of neutral, "divergent" is the axis where you differ the most.
     * Either or both can legitimately be null (nothing shared/divergent enough
     * to call out) rather than forcing an example that isn't really there.
     */
    private Map<String, Object> compatibilityBreakdown(Long targetId, Long callerId) {
        if (callerId == null || callerId.equals(targetId)) {
            return null;
        }
        if (ratingRepo.countByUserId(callerId) < 3 || ratingRepo.countByUserId(targetId) < 3) {
            return null;
        }
        TraitVector callerVec = profileService.vectorOf(profileService.currentProfile(callerId));
        TraitVector targetVec = profileService.vectorOf(profileService.currentProfile(targetId));

        Trait topShared = null;
        double bestSharedCloseness = -1;
        Trait topDivergent = null;
        double bestDivergentGap = -1;

        for (Trait t : Trait.values()) {
            double a = callerVec.get(t);
            double b = targetVec.get(t);
            double gap = Math.abs(a - b);
            boolean bothStrongSameSide = (a >= 0.62 && b >= 0.62) || (a <= 0.38 && b <= 0.38);
            if (bothStrongSameSide) {
                double closeness = 1.0 - gap;
                if (closeness > bestSharedCloseness) {
                    bestSharedCloseness = closeness;
                    topShared = t;
                }
            }
            if (gap > 0.25 && gap > bestDivergentGap) {
                bestDivergentGap = gap;
                topDivergent = t;
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sharedTrait", topShared == null ? null : topShared.label());
        out.put("divergentTrait", topDivergent == null ? null : topDivergent.label());
        return out;
    }

    @Transactional
    public Map<String, Object> follow(Long followerId, Long followeeId) {
        if (followerId.equals(followeeId)) {
            throw new IllegalArgumentException("Cannot follow yourself");
        }
        if (userRepo.findById(followeeId).isEmpty()) {
            throw new IllegalArgumentException("Unknown user " + followeeId);
        }
        if (isBlocked(followerId, followeeId)) {
            throw new IllegalArgumentException("Cannot follow this user");
        }
        if (!followRepo.existsByFollowerIdAndFolloweeId(followerId, followeeId)) {
            followRepo.save(new Follow(followerId, followeeId, Instant.now()));
        }
        return Map.of("status", "success", "following", true);
    }

    @Transactional
    public Map<String, Object> unfollow(Long followerId, Long followeeId) {
        followRepo.deleteByFollowerIdAndFolloweeId(followerId, followeeId);
        return Map.of("status", "success", "following", false);
    }

    /** Hard cap on the follower/following lists a single request returns — see the class-level pagination note. */
    private static final int MAX_CONNECTIONS_PER_PAGE = 200;

    public List<UserSummaryDTO> followers(Long targetId, Long callerId) {
        List<Long> ids = followRepo
                .findByFolloweeIdOrderByCreatedAtDesc(targetId, PageRequest.of(0, MAX_CONNECTIONS_PER_PAGE))
                .stream().map(Follow::getFollowerId).toList();
        return summarize(ids, callerId);
    }

    public List<UserSummaryDTO> following(Long targetId, Long callerId) {
        List<Long> ids = followRepo
                .findByFollowerIdOrderByCreatedAtDesc(targetId, PageRequest.of(0, MAX_CONNECTIONS_PER_PAGE))
                .stream().map(Follow::getFolloweeId).toList();
        return summarize(ids, callerId);
    }

    /**
     * Friend search by partial username — the only way to reach another user
     * today was via a DNA-match or activity-feed entry, which meant there was
     * no way to find someone you know but haven't crossed paths with
     * algorithmically. Username only, not email (see UserRepository) — email
     * search would let anyone probe whether an address has an account here.
     * Reuses summarize()'s existing block-filtering rather than duplicating it.
     */
    public List<UserSummaryDTO> searchUsers(String query, Long callerId) {
        if (query == null || query.trim().length() < 2) {
            return List.of();
        }
        List<Long> ids = userRepo
                .findByUsernameContainingIgnoreCase(query.trim(), PageRequest.of(0, 20))
                .stream()
                .map(User::getId)
                .filter(id -> !id.equals(callerId))
                .toList();
        return summarize(ids, callerId);
    }

    /**
     * Other users ranked by centred-cosine similarity of their whole TraitVector
     * profile to the caller's — the same shape-similarity metric DiscoveryService
     * uses for "Similar Emotional DNA" movies, just applied person-to-person
     * instead of person-to-movie. Requires at least a few rated titles on both
     * sides or the comparison is just noise around 0.5 — callers with a fresh
     * profile get an empty list rather than a meaningless ranking.
     */
    public List<UserSummaryDTO> dnaMatches(Long callerId, int limit) {
        if (ratingRepo.countByUserId(callerId) < 3) {
            return List.of();
        }
        TraitVector callerVec = profileService.vectorOf(profileService.currentProfile(callerId));

        List<User> candidates = userRepo.findAll().stream()
                .filter(u -> !u.getId().equals(callerId))
                .filter(u -> ratingRepo.countByUserId(u.getId()) >= 3)
                .filter(u -> !isBlocked(callerId, u.getId()))
                // A user who opted out of profile visibility shouldn't surface
                // as a suggested match either.
                .filter(User::isProfilePublic)
                .toList();

        List<UserSummaryDTO> ranked = new ArrayList<>();
        Map<Long, Double> scores = new HashMap<>();
        for (User u : candidates) {
            TraitVector otherVec = profileService.vectorOf(profileService.currentProfile(u.getId()));
            scores.put(u.getId(), callerVec.centredCosine(otherVec));
        }
        candidates.stream()
                .sorted(Comparator.comparingDouble((User u) -> scores.get(u.getId())).reversed())
                .limit(limit)
                .forEach(u -> {
                    var archetype = archetypeService.classify(profileService.currentProfile(u.getId()));
                    ranked.add(withAvatar(new UserSummaryDTO(u.getId(), u.getUsername(), archetype.archetype(),
                            followRepo.existsByFollowerIdAndFolloweeId(callerId, u.getId()),
                            scores.get(u.getId())), u));
                });
        return ranked;
    }

    /**
     * Ratings the user chose to write a reflection on (non-blank `moment`) —
     * treated as the implicit "publish this as a review" signal, since ratings
     * with no moment are just private telemetry the user never meant to share.
     */
    public List<Map<String, Object>> reviews(Long targetId, Long callerId, int limit) {
        if (callerId != null && isBlocked(targetId, callerId)) {
            return List.of();
        }
        if (!isProfileVisibleTo(targetId, callerId)) {
            return List.of();
        }
        // Filtering (non-blank moment) and dedupeByTitle both shrink the candidate
        // set, so the DB fetch is sized generously above `limit` rather than exactly
        // `limit` — but still bounded, so a long-time user's entire rating log
        // doesn't load into memory on every profile view (see MAX_CONNECTIONS_PER_PAGE
        // for the same reasoning applied to followers/following).
        int fetchWindow = Math.min(Math.max(limit * 5, limit), 500);
        List<Rating> ratings = dedupeByTitle(ratingRepo
                .findByUserIdOrderByCreatedAtDescIdDesc(targetId, PageRequest.of(0, fetchWindow)).stream()
                .filter(r -> r.getMoment() != null && !r.getMoment().isBlank()))
                .limit(limit)
                .toList();
        return withTitles(ratings);
    }

    /**
     * Re-rating a title is intentionally NOT deduplicated at the data layer (see
     * Rating's own javadoc — each rating is real evidence for trait-evolution
     * replay, not a correction). But a reviews/activity LIST showing the exact
     * same title-plus-rating back to back reads as a glitch, not a rewatch — so
     * the newest-first stream is collapsed to one entry per title here, at the
     * display layer only. Input must already be newest-first so "first seen"
     * means "most recent."
     */
    private java.util.stream.Stream<Rating> dedupeByTitle(java.util.stream.Stream<Rating> newestFirst) {
        java.util.Set<Long> seen = new java.util.HashSet<>();
        return newestFirst.filter(r -> seen.add(r.getTitleId()));
    }

    /** Recent reviews from people the caller follows, newest first. */
    public List<Map<String, Object>> activityFeed(Long callerId, int limit) {
        List<Long> followedIds = followRepo.findByFollowerIdOrderByCreatedAtDesc(callerId).stream()
                .map(Follow::getFolloweeId)
                .filter(id -> !isBlocked(callerId, id))
                .toList();
        if (followedIds.isEmpty()) {
            return List.of();
        }
        java.util.Set<String> seen = new java.util.HashSet<>();
        List<Rating> ratings = ratingRepo.findByUserIdInOrderByCreatedAtDesc(followedIds, PageRequest.of(0, limit * 3))
                .stream()
                .filter(r -> r.getMoment() != null && !r.getMoment().isBlank())
                // Per (user, title), not per title alone — two different people
                // reviewing the same film are two real, distinct feed items.
                .filter(r -> seen.add(r.getUserId() + ":" + r.getTitleId()))
                .limit(limit)
                .toList();
        return withTitles(ratings);
    }

    /** Public (owner-marked-shareable) folders with their items. */
    public List<Map<String, Object>> publicLists(Long targetId, Long callerId) {
        if (callerId != null && isBlocked(targetId, callerId)) {
            return List.of();
        }
        List<WatchlistFolder> folders = folderRepo.findByUserIdOrderByNameAsc(targetId).stream()
                .filter(WatchlistFolder::isPublic)
                .toList();

        List<Map<String, Object>> out = new ArrayList<>();
        for (WatchlistFolder folder : folders) {
            List<WatchlistItem> items = itemRepo.findByFolderIdOrderByAddedAtDesc(folder.getId());

            // Only fetched (and only shown) when the folder actually allows more
            // than one contributor — an ordinary list's items are all obviously
            // the owner's, no need to look up a username per item for that case.
            Map<Long, String> contributorNames = new HashMap<>();
            if (folder.isCollaborative()) {
                List<Long> contributorIds = items.stream().map(WatchlistItem::getUserId).distinct().toList();
                userRepo.findAllById(contributorIds).forEach(u -> contributorNames.put(u.getId(), u.getUsername()));
            }

            List<Map<String, Object>> titleSummaries = new ArrayList<>();
            for (WatchlistItem item : items) {
                Title t = titleRepo.findById(item.getTitleId()).orElse(null);
                if (t == null) {
                    continue;
                }
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("titleId", t.getId());
                m.put("tmdbId", t.getTmdbId());
                m.put("title", t.getTitle());
                m.put("poster", t.getPoster() == null ? "" : t.getPoster());
                if (folder.isCollaborative()) {
                    m.put("addedByUserId", item.getUserId());
                    m.put("addedByUsername", contributorNames.get(item.getUserId()));
                }
                titleSummaries.add(m);
            }

            Map<String, Object> f = new LinkedHashMap<>();
            f.put("folderId", folder.getId());
            f.put("name", folder.getName());
            f.put("collaborative", folder.isCollaborative());
            f.put("itemCount", titleSummaries.size());
            f.put("items", titleSummaries);
            out.add(f);
        }
        return out;
    }

    /** Preview posters shown on a collection card, before a user opens it. */
    private static final int COLLECTION_PREVIEW_COUNT = 4;

    /**
     * Every public folder across every user except the caller's own and
     * anyone blocked either direction, newest first. Small-scale (this app
     * has no pagination on any social list yet — dnaMatches/activityFeed are
     * the same "load everything, let limit trim it" shape), fine at current
     * size, first thing to add real paging to if the catalog of public
     * folders grows past a few hundred.
     */
    public List<Map<String, Object>> discoverCollections(Long callerId, int limit) {
        List<WatchlistFolder> folders = folderRepo.findByIsPublicTrueOrderByCreatedAtDesc().stream()
                .filter(f -> !f.getUserId().equals(callerId))
                .filter(f -> !isBlocked(callerId, f.getUserId()))
                .toList();

        Map<Long, User> owners = new HashMap<>();
        userRepo.findAllById(folders.stream().map(WatchlistFolder::getUserId).distinct().toList())
                .forEach(u -> owners.put(u.getId(), u));

        return folders.stream()
                .sorted(Comparator.comparingLong((WatchlistFolder f) -> collectionFollowRepo.countByFolderId(f.getId())).reversed())
                .map(f -> collectionSummary(f, owners.get(f.getUserId()), callerId))
                .filter(java.util.Objects::nonNull)
                // An empty folder someone made public (often by accident, or a
                // leftover default "Just Mine" shelf) has nothing to preview and
                // nothing worth discovering — surfacing it just makes the whole
                // rail look broken/unfinished. Filtered here rather than at the
                // WatchlistFolder query level since itemCount only exists once
                // collectionSummary has already loaded the folder's items.
                .filter(summary -> ((Integer) summary.get("itemCount")) > 0)
                .limit(limit)
                .toList();
    }

    /** Collections the caller has chosen to follow — same card shape as discoverCollections. */
    public List<Map<String, Object>> followedCollections(Long callerId) {
        List<Long> folderIds = collectionFollowRepo.findByUserIdOrderByCreatedAtDesc(callerId).stream()
                .map(CollectionFollow::getFolderId)
                .toList();
        if (folderIds.isEmpty()) {
            return List.of();
        }
        Map<Long, WatchlistFolder> folders = new HashMap<>();
        folderRepo.findAllById(folderIds).forEach(f -> folders.put(f.getId(), f));

        Map<Long, User> owners = new HashMap<>();
        userRepo.findAllById(folders.values().stream().map(WatchlistFolder::getUserId).distinct().toList())
                .forEach(u -> owners.put(u.getId(), u));

        List<Map<String, Object>> out = new ArrayList<>();
        for (Long folderId : folderIds) {
            WatchlistFolder f = folders.get(folderId);
            // Folder may have gone private or been deleted since the follow —
            // both read as "no longer following" rather than a broken card.
            if (f == null || !f.isPublic()) {
                continue;
            }
            Map<String, Object> summary = collectionSummary(f, owners.get(f.getUserId()), callerId);
            if (summary != null) {
                out.add(summary);
            }
        }
        return out;
    }

    private Map<String, Object> collectionSummary(WatchlistFolder folder, User owner, Long callerId) {
        if (owner == null) {
            return null;
        }
        List<WatchlistItem> items = itemRepo.findByFolderIdOrderByAddedAtDesc(folder.getId());
        List<String> previewPosters = items.stream()
                .limit(COLLECTION_PREVIEW_COUNT)
                .map(item -> titleRepo.findById(item.getTitleId()).orElse(null))
                .filter(java.util.Objects::nonNull)
                .map(Title::getPoster)
                .filter(java.util.Objects::nonNull)
                .toList();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("folderId", folder.getId());
        out.put("name", folder.getName());
        out.put("ownerUserId", owner.getId());
        out.put("ownerUsername", owner.getUsername());
        out.put("ownerAvatarUrl", owner.getAvatarUrl());
        out.put("itemCount", items.size());
        out.put("followerCount", collectionFollowRepo.countByFolderId(folder.getId()));
        out.put("isFollowing", callerId != null && collectionFollowRepo.existsByUserIdAndFolderId(callerId, folder.getId()));
        out.put("previewPosters", previewPosters);
        out.put("collaborative", folder.isCollaborative());
        return out;
    }

    /**
     * @throws IllegalArgumentException if the folder doesn't exist, isn't
     *         public, or belongs to the caller (following your own collection
     *         is a no-op the UI shouldn't even offer, but the endpoint still
     *         guards it server-side).
     */
    @Transactional
    public void followCollection(Long callerId, Long folderId) {
        WatchlistFolder folder = folderRepo.findById(folderId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown collection " + folderId));
        if (!folder.isPublic()) {
            throw new IllegalArgumentException("This collection isn't public.");
        }
        if (folder.getUserId().equals(callerId)) {
            throw new IllegalArgumentException("Cannot follow your own collection.");
        }
        if (isBlocked(callerId, folder.getUserId())) {
            throw new IllegalArgumentException("Cannot follow this collection.");
        }
        if (!collectionFollowRepo.existsByUserIdAndFolderId(callerId, folderId)) {
            collectionFollowRepo.save(new CollectionFollow(callerId, folderId, Instant.now()));
        }
    }

    @Transactional
    public void unfollowCollection(Long callerId, Long folderId) {
        collectionFollowRepo.deleteByUserIdAndFolderId(callerId, folderId);
    }

    private List<UserSummaryDTO> summarize(List<Long> userIds, Long callerId) {
        if (userIds.isEmpty()) {
            return List.of();
        }
        Map<Long, User> users = new HashMap<>();
        userRepo.findAllById(userIds).forEach(u -> users.put(u.getId(), u));

        return userIds.stream()
                .map(users::get)
                .filter(java.util.Objects::nonNull)
                .filter(u -> callerId == null || !isBlocked(callerId, u.getId()))
                .map(u -> {
                    var archetype = archetypeService.classify(profileService.currentProfile(u.getId()));
                    boolean following = callerId != null
                            && followRepo.existsByFollowerIdAndFolloweeId(callerId, u.getId());
                    return withAvatar(new UserSummaryDTO(u.getId(), u.getUsername(), archetype.archetype(), following), u);
                })
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> withTitles(List<Rating> ratings) {
        if (ratings.isEmpty()) {
            return List.of();
        }
        List<Long> titleIds = ratings.stream().map(Rating::getTitleId).distinct().toList();
        Map<Long, Title> titles = new HashMap<>();
        titleRepo.findAllById(titleIds).forEach(t -> titles.put(t.getId(), t));

        List<Long> userIds = ratings.stream().map(Rating::getUserId).distinct().toList();
        Map<Long, User> users = new HashMap<>();
        userRepo.findAllById(userIds).forEach(u -> users.put(u.getId(), u));

        List<Map<String, Object>> out = new ArrayList<>();
        for (Rating r : ratings) {
            Title t = titles.get(r.getTitleId());
            if (t == null) continue;
            User u = users.get(r.getUserId());

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("ratingId", r.getId());
            item.put("userId", r.getUserId());
            item.put("username", u == null ? null : u.getUsername());
            item.put("titleId", t.getId());
            item.put("tmdbId", t.getTmdbId());
            item.put("title", t.getTitle());
            item.put("poster", t.getPoster());
            item.put("overall", r.getOverall());
            item.put("moment", r.getMoment());
            item.put("createdAt", r.getCreatedAt());
            out.add(item);
        }
        return out;
    }
}
