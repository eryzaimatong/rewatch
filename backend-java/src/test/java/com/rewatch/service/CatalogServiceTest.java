package com.rewatch.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

import com.rewatch.model.Title;
import com.rewatch.repository.TitleRepository;

/**
 * Regression coverage for the fresh-database boot crash fixed in
 * relaxOrphanedNotNullColumns(): on a genuinely new Postgres instance (no
 * app ever migrated it via the retired 5-axis Title fields), the orphaned
 * columns never existed at all. The pre-fix code ran `ALTER TABLE ... DROP
 * NOT NULL` unconditionally, Postgres aborted the whole @Transactional
 * run() the instant that failed, and every later query that boot — the
 * catalog seed loop included — failed with "current transaction is
 * aborted". Confirmed live on a first-time Render deploy; local dev never
 * caught it because its database predates this column set.
 */
@ExtendWith(MockitoExtension.class)
class CatalogServiceTest {

    @Mock private TitleRepository titleRepo;
    @Mock private JdbcTemplate jdbc;

    private CatalogService newService() {
        return new CatalogService(titleRepo, jdbc);
    }

    @Test
    void freshDatabaseWithNoOrphanedColumnsDoesNotAttemptAnyAlter() {
        // information_schema reports none of the retired columns exist —
        // the state of a brand new database.
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), anyString())).thenReturn(false);
        when(titleRepo.findByTmdbIdIsNull()).thenReturn(Collections.emptyList());
        when(titleRepo.findByTmdbId(any())).thenReturn(Optional.empty());
        when(titleRepo.save(any(Title.class))).thenAnswer(inv -> inv.getArgument(0));

        // The whole point: boot must not throw on a fresh database.
        newService().run(mockArgs());

        // And it must not even attempt the ALTER when the column was never there —
        // that's what makes this a fresh-DB-safe check rather than a try/catch that
        // merely survives the same wasted, transaction-poisoning statement.
        verify(jdbc, never()).execute(anyString());
    }

    @Test
    void legacyColumnStillPresentGetsRelaxed() {
        // A database that predates the 5-axis Title field removal still has
        // these columns, still NOT NULL — the ALTER must still run for it.
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq("comfort"))).thenReturn(true);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq("romance"))).thenReturn(false);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq("slow_burn"))).thenReturn(false);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq("nostalgia"))).thenReturn(false);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq("sadness"))).thenReturn(false);
        when(titleRepo.findByTmdbIdIsNull()).thenReturn(Collections.emptyList());
        when(titleRepo.findByTmdbId(any())).thenReturn(Optional.empty());
        when(titleRepo.save(any(Title.class))).thenAnswer(inv -> inv.getArgument(0));

        newService().run(mockArgs());

        verify(jdbc).execute("ALTER TABLE titles ALTER COLUMN comfort DROP NOT NULL");
        verify(jdbc, never()).execute("ALTER TABLE titles ALTER COLUMN romance DROP NOT NULL");
    }

    private ApplicationArguments mockArgs() {
        return org.mockito.Mockito.mock(ApplicationArguments.class);
    }
}
