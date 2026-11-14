package com.rewatch.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import com.rewatch.testsupport.RedisTestSupport;

/** Scheduled jobs must run on ONE instance at a time, not once per instance. */
@EnabledIf("com.rewatch.testsupport.RedisTestSupport#available")
class RedisJobLockTest {

    private static String job() {
        return "job-" + UUID.randomUUID();
    }

    @Test
    void onlyOneInstanceRunsTheJobAtATime() throws Exception {
        RedisJobLock instanceA = new RedisJobLock(RedisTestSupport.newTemplate());
        RedisJobLock instanceB = new RedisJobLock(RedisTestSupport.newTemplate());
        String name = job();

        CountDownLatch aRunning = new CountDownLatch(1);
        CountDownLatch releaseA = new CountDownLatch(1);
        var pool = java.util.concurrent.Executors.newSingleThreadExecutor();
        var aRan = pool.submit(() -> instanceA.runIfLeader(name, Duration.ofSeconds(30), () -> {
            aRunning.countDown();
            try { releaseA.await(10, TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
        }));
        assertTrue(aRunning.await(5, TimeUnit.SECONDS));

        AtomicInteger bRuns = new AtomicInteger();
        assertFalse(instanceB.runIfLeader(name, Duration.ofSeconds(30), bRuns::incrementAndGet));
        assertEquals(0, bRuns.get());

        releaseA.countDown();
        assertTrue(aRan.get(5, TimeUnit.SECONDS));
        pool.shutdown();

        // A finished and released the lock, so B can lead the next run.
        assertTrue(instanceB.runIfLeader(name, Duration.ofSeconds(30), bRuns::incrementAndGet));
        assertEquals(1, bRuns.get());
    }

    @Test
    void aCrashedHolderCannotWedgeTheJobForever() throws Exception {
        RedisJobLock crashed = new RedisJobLock(RedisTestSupport.newTemplate());
        RedisJobLock other = new RedisJobLock(RedisTestSupport.newTemplate());
        String name = job();
        // Simulate a holder that took the lock and died without releasing it.
        assertTrue(crashed.tryAcquire(name, Duration.ofMillis(300)) != null);
        assertFalse(other.runIfLeader(name, Duration.ofSeconds(30), () -> { }));
        Thread.sleep(450);
        assertTrue(other.runIfLeader(name, Duration.ofSeconds(30), () -> { }));
    }

    @Test
    void releasingDoesNotStealALockThatExpiredAndWasTakenByAnother() throws Exception {
        RedisJobLock a = new RedisJobLock(RedisTestSupport.newTemplate());
        RedisJobLock b = new RedisJobLock(RedisTestSupport.newTemplate());
        String name = job();
        String aToken = a.tryAcquire(name, Duration.ofMillis(200));
        Thread.sleep(350);                                   // A's lock expires while A is "still running"
        String bToken = b.tryAcquire(name, Duration.ofSeconds(30));
        assertTrue(bToken != null);
        a.release(name, aToken);                             // A finishing late must not free B's lock
        assertTrue(a.tryAcquire(name, Duration.ofSeconds(30)) == null);
    }
}
