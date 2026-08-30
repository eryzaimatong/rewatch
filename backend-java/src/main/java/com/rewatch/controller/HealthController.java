package com.rewatch.controller;

import java.sql.Connection;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Liveness/readiness probe for orchestrators (Docker healthcheck, load
 * balancer, etc). Checks the DB connection rather than just "the JVM is up" —
 * a backend that's running but can't reach Postgres should report unhealthy.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        // TEMPORARY: dbPingMs added for the D1a Render-Oregon-to-Neon RTT
        // measurement (real number requested, not an estimate — free-tier
        // Render has no SSH/one-off-job access to measure this any other
        // way). Times conn.isValid(2), which still sends a real round trip
        // to Neon even over an already-pooled connection. Remove once the
        // region fix lands and this number is no longer needed.
        try {
            long start = System.nanoTime();
            try (Connection conn = dataSource.getConnection()) {
                boolean ok = conn.isValid(2);
                long elapsedMs = (System.nanoTime() - start) / 1_000_000;
                if (ok) {
                    return ResponseEntity.ok(Map.of("status", "ok", "db", "up", "dbPingMs", elapsedMs));
                }
            }
        } catch (Exception e) {
            // fall through to the down response below
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("status", "error", "db", "down"));
    }
}
