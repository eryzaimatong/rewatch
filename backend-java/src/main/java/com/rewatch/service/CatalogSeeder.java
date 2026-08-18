package com.rewatch.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.rewatch.repository.TitleRepository;

/**
 * Runs once per boot, after the app is already accepting requests (Spring
 * Boot starts the embedded server before invoking ApplicationRunners). A
 * fresh deploy previously had an empty `titles` table until an operator
 * manually POSTed /api/admin/expand-catalog — a step that wasn't documented
 * anywhere, so the flagship Mood Search feature (which only searches the
 * local catalog, not TMDB live) silently returned nothing for the first
 * users on any new deployment. This closes that gap automatically instead
 * of relying on a human remembering an undocumented curl command.
 *
 * Runs on a plain background thread, not the main startup thread — bulkExpand
 * makes hundreds of rate-limited TMDB calls and can take minutes; nothing
 * about serving requests should wait on it.
 */
@Component
public class CatalogSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CatalogSeeder.class);

    /**
     * Below this, the catalog is thin enough that Mood Search / Discovery
     * would feel broken (empty or near-empty results) rather than merely
     * limited — well under the seed target below, so this only fires on a
     * genuinely fresh/empty deploy, not a catalog that simply hasn't been
     * expanded to full breadth yet.
     */
    private static final int MIN_CATALOG_SIZE = 200;
    // Raised from 1,500 — at that size, a title search or a specific mood
    // filter routinely came up thin or empty for anything not squarely in
    // the most-popular-of-the-most-popular slice TMDB's top lists surface.
    private static final int SEED_TARGET = 6000;

    private final TitleRepository titleRepo;
    private final EnrichmentService enrichmentService;
    private final TmdbClient tmdb;

    public CatalogSeeder(TitleRepository titleRepo, EnrichmentService enrichmentService, TmdbClient tmdb) {
        this.titleRepo = titleRepo;
        this.enrichmentService = enrichmentService;
        this.tmdb = tmdb;
    }

    @Override
    public void run(ApplicationArguments args) {
        long count = titleRepo.count();
        if (count >= MIN_CATALOG_SIZE) {
            log.info("Catalog seeder: {} titles already present, skipping auto-seed.", count);
            return;
        }
        if (!tmdb.isConfigured()) {
            log.warn("Catalog seeder: only {} titles and no TMDB_API_KEY configured — "
                    + "the catalog will stay this thin until a key is set and "
                    + "POST /api/admin/expand-catalog is run.", count);
            return;
        }
        log.info("Catalog seeder: only {} titles found, auto-seeding to ~{} in the background.", count, SEED_TARGET);
        Thread seedThread = new Thread(() -> {
            try {
                var result = enrichmentService.bulkExpand(SEED_TARGET);
                log.info("Catalog seeder: finished — {}", result);
            } catch (Exception e) {
                log.error("Catalog seeder: bulkExpand failed", e);
            }
        }, "catalog-seeder");
        seedThread.setDaemon(true);
        seedThread.start();
    }
}
