package com.rewatch.controller;

import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rewatch.security.SecurityUtil;
import com.rewatch.service.StreakService;

@RestController
@RequestMapping("/api/streak")
public class StreakController {

    private final StreakService streakService;

    public StreakController(StreakService streakService) {
        this.streakService = streakService;
    }

    @GetMapping("/{userId}")
    public Map<String, Object> get(@PathVariable Long userId, Authentication authentication) {
        SecurityUtil.requireSelf(authentication, userId);
        StreakService.Streak streak = streakService.compute(userId);
        return Map.of(
                "current", streak.current(),
                "longest", streak.longest(),
                "activeToday", streak.activeToday());
    }
}
