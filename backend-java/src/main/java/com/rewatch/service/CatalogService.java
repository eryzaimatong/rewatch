package com.rewatch.service;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rewatch.model.Title;
import com.rewatch.repository.TitleRepository;

/**
 * Seeds a small offline catalog so the app is usable without TMDB reachable.
 *
 * Seeding is an UPSERT keyed on tmdbId — never a delete. The previous
 * implementation was split across two beans that fought each other: CatalogService
 * seeded 8 rich titles from @PostConstruct, then DataSeeder (a CommandLineRunner,
 * so it ran later) called deleteAll() and inserted 5 stripped-down rows. The rich
 * catalog was silently destroyed on every single boot, and CatalogService's
 * `count() > 0` guard never tripped because the table had just been emptied.
 *
 * Upsert also keeps this safe once ratings hold a foreign key to titles: reseeding
 * must never orphan a user's rating history.
 */
@Service
public class CatalogService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CatalogService.class);

    private final TitleRepository titleRepo;
    private final JdbcTemplate jdbc;

    public CatalogService(TitleRepository titleRepo, JdbcTemplate jdbc) {
        this.titleRepo = titleRepo;
        this.jdbc = jdbc;
    }

    /**
     * Columns from the retired 5-axis Title fields (comfort/romance/slow_burn/
     * nostalgia/sadness). `ddl-auto=update` never drops a column or a constraint,
     * so these are still NOT NULL in Postgres even though no Java field writes to
     * them anymore — every insert failed until this ran once. Safe to run every
     * boot: DROP NOT NULL on an already-nullable column is a no-op.
     */
    private static final String[] ORPHANED_NOT_NULL_COLUMNS = {
        "comfort", "romance", "slow_burn", "nostalgia", "sadness"
    };

    // tmdbId | title | year | type | poster | synopsis | voteAvg | popularity | genreIds | lang
    private static final String[][] SEED = {
        {"157336", "Interstellar", "2014", "movie", "/gEU2QniE6E77NI6lCU6MxlNBvIx.jpg",
         "A team of explorers travel through a wormhole in space in an attempt to ensure humanity's survival.",
         "8.4", "88.5", "12,18,878", "en"},

        {"496243", "Parasite", "2019", "movie", "/7IiTTgloJzvGI1TAYymCfbfl3vT.jpg",
         "Greed and class discrimination threaten the newly formed symbiotic relationship between the wealthy Park family and the destitute Kim clan.",
         "8.5", "92.0", "35,53,18", "ko"},

        {"339788", "Arrival", "2016", "movie", "/pEFRzXtLmxYNjGd0XqJDHPDFKB2.jpg",
         "A linguist works with the military to communicate with alien lifeforms after twelve mysterious spacecraft appear around the world.",
         "7.6", "79.4", "18,878,9648", "en"},

        {"76479", "The Boys", "2019", "series", "/2jnXKpx2L2n34bT83H26WcftM22.jpg",
         "A group of vigilantes set out to take down corrupt superheroes who abuse their powers.",
         "8.5", "95.1", "28,878,18", "en"},

        {"93405", "Squid Game", "2021", "kdrama", "/dDlEmu3EZ0Pgg93K2SVNLCjCSvE.jpg",
         "Hundreds of cash-strapped players accept a strange invitation to compete in children's games with deadly stakes.",
         "7.8", "98.0", "28,9648,18", "ko"},

        {"653346", "Our Beloved Summer", "2021", "kdrama", "/6d6c483b4e6d4c6d6c6d6c6d6c.jpg",
         "Ex-lovers are pulled back in front of the camera years after the documentary they filmed in high school goes viral.",
         "8.6", "84.2", "10749,35,18", "ko"},

        {"37854", "One Piece", "1999", "anime", "/e3NBGiAifW9Xt8xD5tpARskjccO.jpg",
         "Monkey D. Luffy and his pirate crew set off to find the legendary treasure known as One Piece.",
         "8.7", "99.5", "16,12,28", "ja"},

        {"19995", "Avatar", "2009", "movie", "/kyeqWdyUXW608qlYkRqosgNaSGj.jpg",
         "A paraplegic marine dispatched to the moon Pandora on a unique mission becomes torn between following orders and protecting an alien civilization.",
         "7.6", "91.0", "28,12,14,878", "en"}
    };

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        relaxOrphanedNotNullColumns();

        // One-time cleanup of rows written by the deleted DataSeeder. Those rows have
        // no tmdbId, no poster and no derived features, so they cannot be upserted or
        // enriched — they can only be replaced.
        List<Title> legacy = titleRepo.findByTmdbIdIsNull();
        if (!legacy.isEmpty()) {
            log.info("Removing {} legacy catalog rows with no tmdbId (written by the old DataSeeder)",
                    legacy.size());
            titleRepo.deleteAll(legacy);
        }

        int created = 0;
        int updated = 0;

        for (String[] row : SEED) {
            Integer tmdbId = Integer.valueOf(row[0]);
            Optional<Title> existing = titleRepo.findByTmdbId(tmdbId);

            Title t = existing.orElseGet(Title::new);
            boolean isNew = existing.isEmpty();

            t.setTmdbId(tmdbId);
            t.setTitle(row[1]);
            t.setYear(Integer.parseInt(row[2]));
            t.setType(row[3]);
            t.setPoster(row[4]);
            t.setSynopsis(row[5]);
            t.setVoteAverage(Double.valueOf(row[6]));
            t.setVoteCount(1000);
            t.setQuality(Double.parseDouble(row[6]) / 2.0);
            t.setPopularity(Double.parseDouble(row[7]));
            t.setGenreIds(row[8]);
            t.setOriginalLanguage(row[9]);

            // Deliberately does NOT touch the derived trait columns or enrichedAt —
            // FeatureDeriver owns those, and reseeding must not discard enrichment.
            titleRepo.save(t);

            if (isNew) {
                created++;
            } else {
                updated++;
            }
        }

        log.info("Catalog seed complete: {} created, {} updated, {} titles total",
                created, updated, titleRepo.count());
    }

    /**
     * Checks information_schema before attempting each ALTER, rather than just
     * catching the exception if the column doesn't exist — Postgres aborts the
     * whole surrounding transaction the instant any statement fails, and a caught
     * Java exception doesn't undo that. run() is @Transactional across this call
     * AND the catalog upsert loop after it, so on a genuinely fresh database
     * (these columns never existed at all, not even a "already dropped" case)
     * the first failed ALTER poisoned the transaction and cascaded into every
     * later query in this same boot failing with "current transaction is
     * aborted" — confirmed live on a first-time Render deploy against a brand
     * new Postgres instance, something the long-lived local dev database
     * (which predates this column set entirely) never exercised.
     */
    private void relaxOrphanedNotNullColumns() {
        for (String column : ORPHANED_NOT_NULL_COLUMNS) {
            Boolean exists = jdbc.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM information_schema.columns "
                            + "WHERE table_name = 'titles' AND column_name = ?)",
                    Boolean.class, column);
            if (Boolean.TRUE.equals(exists)) {
                jdbc.execute("ALTER TABLE titles ALTER COLUMN " + column + " DROP NOT NULL");
            }
        }
    }

    public List<Title> getAllTitles() {
        return titleRepo.findAll();
    }

    public Title getTitleById(Long id) {
        return titleRepo.findById(id).orElse(null);
    }
}
