package com.rewatch.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.junit.jupiter.api.Test;

/**
 * Loads application.properties directly (no Spring context) so this fails
 * on the actual realistic risk — a typo in the property key or value —
 * rather than exercising Spring Boot/HikariCP's own well-established
 * "spring.datasource.hikari.* binds onto HikariConfig" machinery, which is
 * documented framework behavior, not something this app invented.
 *
 * What this does NOT verify, and can't without a live Postgres connection
 * this test environment doesn't have (no H2/testcontainers dependency in
 * this project, and using the local dev Postgres service would mean
 * hunting for its credentials): that Postgres actually enforces
 * statement_timeout once HikariCP runs this SQL on a real connection. That
 * half rests on Postgres's own documented SET statement_timeout semantics,
 * not on anything specific to this codebase — left explicitly unverified
 * by live query.
 */
class DataSourceConfigTest {

    @Test
    void statementTimeoutIsConfiguredAndBoundedWellBelowTheFrontendFetchBudget() throws IOException {
        Properties props = new Properties();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            assertThat(in).as("application.properties must be on the test classpath").isNotNull();
            props.load(in);
        }

        String initSql = props.getProperty("spring.datasource.hikari.connection-init-sql");
        assertThat(initSql).isEqualTo("SET statement_timeout = 30000");

        long timeoutMs = Long.parseLong(initSql.replaceAll("[^0-9]", ""));
        // 180s is sessionGuard.js's frontend fetch budget (FETCH_TIMEOUT_MS) —
        // the database must give up well before the browser does, or the
        // browser's own timeout fires first and the client never even sees
        // the clean error this statement_timeout was meant to produce.
        long frontendFetchBudgetMs = 180_000;
        assertThat(timeoutMs).isLessThan(frontendFetchBudgetMs);
        assertThat(timeoutMs).isPositive();
    }
}
