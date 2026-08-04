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
import com.rewatch.model.Follow;
import com.rewatch.model.Rating;
import com.rewatch.model.Title;
import com.rewatch.model.Trait;
import com.rewatch.model.TraitNode;
import com.rewatch.model.TraitVector;
import com.rewatch.model.User;
import com.rewatch.model.WatchlistFolder;
import com.rewatch.model.WatchlistItem;
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
    private final RatingRepository ratingRepo;
    private final TitleRepository titleRepo;
    private final WatchlistFolderRepository folderRepo;
    private final WatchlistItemRepository itemRepo;
    private final ProfileService profileService;
    private final ArchetypeService archetypeService;

    public SocialService(UserRepository userRepo, FollowRepository followRepo, RatingRepository ratingRepo,
                         TitleRepository titleRepo, WatchlistFolderRepository folderRepo,
                         WatchlistItemRepository itemRepo, ProfileService profileService,
                         ArchetypeService archetypeService) {
        this.userRepo = userRepo;
        this.followRepo = followRepo;
        this.ratingRepo = ratingRepo;
        this.titleRepo = titleRepo;
        this.folderRepo = folderRepo;
        this.itemRepo = itemRepo;
        this.profileService = profileService;
        this.archetypeService = archetypeService;
    }

    public Map<String, Object> publicProfile(Long targetId, Long callerId) {
        User user = userRepo.findById(targetId).orElse(null);
        if (user == null) {
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
        res.put("archetype", archetype.archetype());
        res.put("archetypeBlurb", archetype.blurb());
        res.put("ratingCount", ratingRepo.countByUserId(targetId));
        res.put("followerCount", followRepo.countByFolloweeId(targetId));
        res.put("followingCount", followRepo.countByFollowerId(targetId));
        res.put("isFollowing", callerId != null && followRepo.existsByFollowerIdAndFolloweeId(callerId, targetId));
        res.put("isSelf", callerId != null && callerId.equals(targetId));
        res.put("traits", traits);
        return res;
    }

    @Transactional
    public Map<String, Object> follow(Long followerId, Long followeeId) {
        if (followerId.equals(followeeId)) {
            throw new IllegalArgumentException("Cannot follow yourself");
        }
        if (userRepo.findById(followeeId).isEmpty()) {
            throw new IllegalArgumentException("Unknown user " + followeeId);
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

    public List<UserSummaryDTO> followers(Long targetId, Long callerId) {
        List<Long> ids = followRepo.findByFolloweeIdOrderByCreatedAtDesc(targetId).stream()
                .map(Follow::getFollowerId).toList();
        return summarize(ids, callerId);
    }

    public List<UserSummaryDTO> following(Long targetId, Long callerId) {
        List<Long> ids = followRepo.findByFollowerIdOrderByCreatedAtDesc(targetId).stream()
                .map(Follow::getFolloweeId).toList();
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
                    ranked.add(new UserSummaryDTO(u.getId(), u.getUsername(), archetype.archetype(),
                            followRepo.existsByFollowerIdAndFolloweeId(callerId, u.getId()),
                            scores.get(u.getId())));
                });
        return ranked;
    }

    /**
     * Ratings the user chose to write a reflection on (non-blank `moment`) —
     * treated as the implicit "publish this as a review" signal, since ratings
     * with no moment are just private telemetry the user never meant to share.
     */
    public List<Map<String, Object>> reviews(Long targetId, int limit) {
        List<Rating> ratings = ratingRepo.findByUserIdOrderByCreatedAtDescIdDesc(targetId).stream()
                .filter(r -> r.getMoment() != null && !r.getMoment().isBlank())
                .limit(limit)
                .toList();
        return withTitles(ratings);
    }

    /** Recent reviews from people the caller follows, newest first. */
    public List<Map<String, Object>> activityFeed(Long callerId, int limit) {
        List<Long> followedIds = followRepo.findByFollowerIdOrderByCreatedAtDesc(callerId).stream()
                .map(Follow::getFolloweeId).toList();
        if (followedIds.isEmpty()) {
            return List.of();
        }
        List<Rating> ratings = ratingRepo.findByUserIdInOrderByCreatedAtDesc(followedIds, PageRequest.of(0, limit * 3))
                .stream()
                .filter(r -> r.getMoment() != null && !r.getMoment().isBlank())
                .limit(limit)
                .toList();
        return withTitles(ratings);
    }

    /** Public (owner-marked-shareable) folders with their items. */
    public List<Map<String, Object>> publicLists(Long targetId) {
        List<WatchlistFolder> folders = folderRepo.findByUserIdOrderByNameAsc(targetId).stream()
                .filter(WatchlistFolder::isPublic)
                .toList();

        List<Map<String, Object>> out = new ArrayList<>();
        for (WatchlistFolder folder : folders) {
            List<WatchlistItem> items = itemRepo.findByFolderIdOrderByAddedAtDesc(folder.getId());
            List<Map<String, Object>> titleSummaries = items.stream()
                    .map(item -> titleRepo.findById(item.getTitleId()).orElse(null))
                    .filter(java.util.Objects::nonNull)
                    .map(t -> (Map<String, Object>) (Map<String, ?>) Map.of(
                            "titleId", t.getId(), "tmdbId", t.getTmdbId(),
                            "title", t.getTitle(), "poster", t.getPoster() == null ? "" : t.getPoster()))
                    .toList();

            Map<String, Object> f = new LinkedHashMap<>();
            f.put("folderId", folder.getId());
            f.put("name", folder.getName());
            f.put("itemCount", titleSummaries.size());
            f.put("items", titleSummaries);
            out.add(f);
        }
        return out;
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
                .map(u -> {
                    var archetype = archetypeService.classify(profileService.currentProfile(u.getId()));
                    boolean following = callerId != null
                            && followRepo.existsByFollowerIdAndFolloweeId(callerId, u.getId());
                    return new UserSummaryDTO(u.getId(), u.getUsername(), archetype.archetype(), following);
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
