package com.rewatch.service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.rewatch.dto.RatingDTO;
import com.rewatch.dto.WatchlistRequests.AddItem;
import com.rewatch.features.GenreLexicon;
import com.rewatch.model.Title;
import com.rewatch.model.User;
import com.rewatch.model.WatchlistFolder;
import com.rewatch.repository.TitleRepository;
import com.rewatch.repository.UserRepository;

/**
 * Runs once per boot, after CatalogSeeder — a fresh deploy previously had
 * real users landing on a Community tab with nothing in it anywhere
 * (Similar TasteDNA, Collections, From People You Follow all empty), which
 * reads as an abandoned app rather than a new one, on day one specifically
 * when the impression matters most. Seeds a handful of accounts with real
 * rating history — genuinely computed TasteDNA vectors via the same
 * RatingService.submit() path a real user's ratings go through, not
 * hand-rolled trait numbers — and one public collection each, so Discovery
 * has something real to surface before the first real user has invited
 * anyone.
 *
 * Idempotent the same way CatalogSeeder is: checks whether its own seed
 * already ran (the first persona's username already existing) and skips
 * entirely if so, so this is safe to leave running on every boot rather
 * than needing to be remembered as a one-off manual step.
 */
@Component
public class DemoCommunitySeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoCommunitySeeder.class);

    private record Persona(String username, String bio, String genre, String folderName, String folderBlurb) {}

    private static final List<Persona> PERSONAS = List.of(
            new Persona("demo_marcus", "Horror movies are my comfort food.", "Horror",
                    "Lights Off, Doors Locked", "Nothing better than a good scare after dark."),
            new Persona("demo_priya", "Give me swoony chemistry or give me nothing.", "Romance",
                    "Slow Burn Favorites", "The ones that made me put my phone down."),
            new Persona("demo_sam", "Prestige drama, slow and devastating.", "Drama",
                    "Devastate Me Gently", "Beautifully sad, every single one."),
            new Persona("demo_jules", "Big ideas, bigger universes.", "Sci-Fi",
                    "Worlds I'd Live In", "Science fiction that actually thinks."),
            new Persona("demo_theo", "Life's too short for movies that don't make me laugh.", "Comedy",
                    "Guaranteed Laughs", "My go-to rewatch list when I need to feel good."),
            new Persona("demo_ana", "Edge of my seat or it didn't happen.", "Thriller",
                    "Can't Look Away", "Tense, twisty, no filler."),
            new Persona("demo_kai", "Dragons, magic, found family, every time.", "Fantasy",
                    "Magic & Found Family", "Escapism done right."),
            new Persona("demo_riley", "Animation is a medium, not a genre for kids.", "Animation",
                    "Animation for Grown-Ups", "Yes, adults are allowed to love these too.")
    );

    /** How many titles from the persona's genre get rated. */
    private static final int RATINGS_PER_PERSONA = 18;
    /** Of those, how many also land in the persona's public collection. */
    private static final int COLLECTION_SIZE = 8;
    /** Matches Recommender.MIN_VOTE_COUNT_FOR_RECOMMENDATION — a demo profile
     *  built on genuinely well-known titles produces a more legible TasteDNA
     *  archetype than one padded with obscure catalog filler. */
    private static final int MIN_VOTE_COUNT = 150;

    private final UserRepository userRepo;
    private final TitleRepository titleRepo;
    private final PasswordEncoder passwordEncoder;
    private final RatingService ratingService;
    private final WatchlistService watchlistService;

    public DemoCommunitySeeder(UserRepository userRepo, TitleRepository titleRepo, PasswordEncoder passwordEncoder,
                               RatingService ratingService, WatchlistService watchlistService) {
        this.userRepo = userRepo;
        this.titleRepo = titleRepo;
        this.passwordEncoder = passwordEncoder;
        this.ratingService = ratingService;
        this.watchlistService = watchlistService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepo.findByUsername(PERSONAS.get(0).username()) != null) {
            log.info("Demo community seeder: already seeded, skipping.");
            return;
        }

        List<Title> all = titleRepo.findAll();
        if (all.size() < 500) {
            // Catalog hasn't finished (auto-)seeding yet — CatalogSeeder's own
            // bulkExpand runs on a background thread and can take minutes, so
            // a fresh deploy can reach here before it's done. Rather than
            // building demo profiles off a near-empty catalog, this simply
            // doesn't run this boot; it tries again next restart, same as any
            // operator-triggered redeploy would naturally provide.
            log.info("Demo community seeder: catalog too thin ({} titles) yet, skipping this boot.", all.size());
            return;
        }

        int seeded = 0;
        for (Persona p : PERSONAS) {
            try {
                seedPersona(p, all);
                seeded++;
            } catch (Exception e) {
                // One persona failing (an unlucky genre with too few candidates,
                // a transient constraint violation) shouldn't take the rest of
                // startup down with it, or block the seven personas that would
                // otherwise have worked.
                log.error("Demo community seeder: failed to seed {}", p.username(), e);
            }
        }
        log.info("Demo community seeder: seeded {}/{} demo accounts.", seeded, PERSONAS.size());
    }

    private void seedPersona(Persona p, List<Title> all) {
        List<Title> candidates = all.stream()
                .filter(t -> t.getSynopsis() != null && !t.getSynopsis().isBlank())
                .filter(t -> t.getPoster() != null && !t.getPoster().isBlank())
                .filter(t -> t.getVoteCount() != null && t.getVoteCount() >= MIN_VOTE_COUNT)
                .filter(t -> GenreLexicon.namesFor(t.getGenreIds()).contains(p.genre()))
                .sorted(Comparator.comparingDouble(Title::getPopularity).reversed())
                .limit(RATINGS_PER_PERSONA)
                .toList();

        if (candidates.size() < 6) {
            log.warn("Demo community seeder: only {} {} candidates found for {}, skipping.",
                    candidates.size(), p.genre(), p.username());
            return;
        }

        User user = new User();
        user.setUsername(p.username());
        user.setEmail(p.username() + "@demo.rewatch.local");
        user.setPassword(passwordEncoder.encode(java.util.UUID.randomUUID().toString()));
        user.setBio(p.bio());
        user = userRepo.save(user);
        Long userId = user.getId();

        WatchlistFolder folder = watchlistService.createFolder(userId, p.folderName());

        for (int i = 0; i < candidates.size(); i++) {
            Title t = candidates.get(i);

            // Mostly enthusiastic (this is a persona defined by loving this
            // genre) with real variance rather than a flat 5 across the board
            // — an unbroken 5-star streak reads as fake in a way a genuine
            // fan's ratings don't.
            int overall = switch (i % 5) {
                case 0 -> 5;
                case 1 -> 4;
                case 2 -> 5;
                case 3 -> 4;
                default -> 3;
            };

            RatingDTO dto = new RatingDTO();
            dto.setUserId(userId);
            dto.setTitleId(t.getId());
            dto.setOverall(overall);
            ratingService.submit(dto);

            if (i < COLLECTION_SIZE) {
                AddItem item = new AddItem();
                item.setUserId(userId);
                item.setTitleId(t.getId());
                item.setFolderId(folder.getId());
                watchlistService.addItem(item);
            }
        }

        watchlistService.setFolderVisibility(userId, folder.getId(), true);
    }
}
