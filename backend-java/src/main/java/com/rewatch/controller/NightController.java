package com.rewatch.controller;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rewatch.security.RateLimiterService;
import com.rewatch.security.SecurityUtil;
import com.rewatch.service.NightService;
import com.rewatch.service.NightService.Joined;

/**
 * Movie Night — the no-account group pick. Public on purpose (a friend opens a
 * shared link cold); abuse is bounded by IP rate limits, an 8-person cap, name
 * sanitising and a 24-hour expiry. It never touches a user's private data.
 */
@RestController
@RequestMapping("/api/nights")
public class NightController {

    private static final int MAX_CREATES_PER_HOUR = 20;
    private static final int MAX_JOINS_PER_HOUR = 60;

    private final NightService service;
    private final RateLimiterService rateLimiter;

    public NightController(NightService service, RateLimiterService rateLimiter) {
        this.service = service;
        this.rateLimiter = rateLimiter;
    }

    public static class JoinRequest {
        @NotBlank @Size(max = 60) private String name;
        @NotNull @Size(min = 5, max = 5) private List<Integer> answers;

        public String getName() { return name; }
        public void setName(String v) { this.name = v; }
        public List<Integer> getAnswers() { return answers; }
        public void setAnswers(List<Integer> v) { this.answers = v; }
    }

    @GetMapping("/questions")
    public List<Map<String, Object>> questions() {
        return service.questions();
    }

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody JoinRequest req, HttpServletRequest http) {
        String key = "night-create:" + SecurityUtil.clientIp(http);
        if (!rateLimiter.allow(key, MAX_CREATES_PER_HOUR, Duration.ofHours(1))) {
            return rateLimiter.tooManyRequests(key, Duration.ofHours(1), "Too many movie nights started from your network.");
        }
        return respond(service.create(req.getName(), req.getAnswers()));
    }

    @PostMapping("/{code}/join")
    public ResponseEntity<?> join(@PathVariable String code, @Valid @RequestBody JoinRequest req, HttpServletRequest http) {
        String key = "night-join:" + SecurityUtil.clientIp(http);
        if (!rateLimiter.allow(key, MAX_JOINS_PER_HOUR, Duration.ofHours(1))) {
            return rateLimiter.tooManyRequests(key, Duration.ofHours(1), "Too many joins from your network.");
        }
        return respond(service.join(code, req.getName(), req.getAnswers()));
    }

    @GetMapping("/{code}")
    public ResponseEntity<?> view(@PathVariable String code) {
        Map<String, Object> night = service.view(code);
        if (night == null) {
            return error(HttpStatus.NOT_FOUND, "This movie night doesn't exist or has expired.");
        }
        return ResponseEntity.ok(night);
    }

    private ResponseEntity<?> respond(Joined j) {
        return switch (j.status()) {
            case OK -> ResponseEntity.ok(Map.of("code", j.code(), "memberId", j.memberId()));
            case NOT_FOUND -> error(HttpStatus.NOT_FOUND, "This movie night doesn't exist or has expired.");
            case FULL -> error(HttpStatus.CONFLICT, "This movie night is full (8 people max).");
            case BAD_INPUT -> error(HttpStatus.BAD_REQUEST, "Add a name and answer all five questions.");
        };
    }

    private static ResponseEntity<?> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("status", "error", "message", message));
    }
}
