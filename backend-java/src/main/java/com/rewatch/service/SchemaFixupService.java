package com.rewatch.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Patches schema drift that ddl-auto=update cannot fix on its own (it only adds
 * columns/tables, never alters an existing constraint) — see
 * CatalogService.relaxOrphanedNotNullColumns for the earlier instance of this
 * same problem. Every patch here must be idempotent and safe to run on every
 * boot, in every environment (fresh local DB, an existing dev DB, prod).
 */
@Service
public class SchemaFixupService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaFixupService.class);

    private final JdbcTemplate jdbc;

    public SchemaFixupService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        widenNotificationsTypeCheck();
    }

    /**
     * notifications_type_check was hand-written against Notification.Type's
     * original three values (NEW_FOLLOWER, TASTE_MILESTONE, DNA_MATCH). Adding
     * ACHIEVEMENT_UNLOCKED to the Java enum without this meant every unlock
     * notification 500'd on insert. Re-asserting the full constraint on every
     * boot means the next enum addition only needs a change here, not a manual
     * ALTER run by hand against each environment.
     */
    private void widenNotificationsTypeCheck() {
        try {
            jdbc.execute("ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check");
            jdbc.execute("ALTER TABLE notifications ADD CONSTRAINT notifications_type_check "
                    + "CHECK (type IN ('NEW_FOLLOWER','TASTE_MILESTONE','DNA_MATCH','ACHIEVEMENT_UNLOCKED',"
                    + "'REVIEW_LIKED','REVIEW_COMMENTED'))");
        } catch (Exception e) {
            log.debug("Could not widen notifications_type_check (table may not exist yet): {}", e.getMessage());
        }
    }
}
