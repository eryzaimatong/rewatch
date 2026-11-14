package com.rewatch.config;

import java.time.Duration;

/**
 * Leader election for scheduled jobs. With several backend instances every
 * {@code @Scheduled} method fires on every instance; wrapping the body in
 * {@link #runIfLeader} makes sure only one of them does the work at a time.
 */
public interface JobLock {

    /** Always leads: the single-instance default, where there is nobody to compete with. */
    JobLock NONE = (name, ttl, job) -> {
        job.run();
        return true;
    };

    /**
     * Runs {@code job} if no other instance holds the lock named {@code name}.
     * {@code ttl} bounds how long a holder can keep it, so a crashed instance
     * cannot block the job forever; set it above the job's expected duration.
     *
     * @return true if this instance ran the job
     */
    boolean runIfLeader(String name, Duration ttl, Runnable job);
}
