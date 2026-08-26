package com.rewatch.security;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only controller used solely by SecurityConfigTest to probe the real
 * SecurityFilterChain's route matrix. A top-level class (not nested inside
 * the test) so @WebMvcTest's component scan finds it unambiguously — every
 * handler just returns 200, so "reached the handler" is unambiguous: a
 * 401/403 means the security layer actually blocked the request first.
 */
@RestController
public class SecurityConfigProbeController {
    @GetMapping("/api/health") public String health() { return "ok"; }
    @GetMapping("/api/titles") public String titles() { return "ok"; }
    @GetMapping("/api/movies/popular") public String popular() { return "ok"; }
    @GetMapping("/api/compatibility/quiz") public String quiz() { return "ok"; }
    @PostMapping("/api/compatibility/check") public String check() { return "ok"; }
    @PostMapping("/api/auth/login") public String login() { return "ok"; }
    @GetMapping("/api/admin/reports") public String adminReports() { return "ok"; }
    @GetMapping("/api/watch-status/{userId}") public String watchStatus() { return "ok"; }
}
