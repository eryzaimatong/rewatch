package com.rewatch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.rewatch.config.JobLock;

class EnrichmentLeaderTest {

    @Test
    void theSweepOnlyRunsOnTheInstanceThatHoldsTheLock() {
        // Collaborators are null on purpose: if the sweep body ran at all it would NPE.
        EnrichmentService service = new EnrichmentService(null, null, null);
        AtomicInteger asked = new AtomicInteger();
        service.setJobLock(new JobLock() {
            public boolean runIfLeader(String name, Duration ttl, Runnable job) {
                asked.incrementAndGet();
                return false; // another instance is leading
            }
        });
        service.enrichBatch();
        assertEquals(1, asked.get());
    }
}
