package com.rewatch.controller;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.rewatch.dto.MovieDTO;
import com.rewatch.security.SecurityUtil;
import com.rewatch.service.DiscoveryService;

@RestController
@RequestMapping("/api/discovery")
public class DiscoveryController {

    private final DiscoveryService discoveryService;

    public DiscoveryController(DiscoveryService discoveryService) {
        this.discoveryService = discoveryService;
    }

    @GetMapping("/hidden-gems/{userId}")
    public List<MovieDTO> hiddenGems(@PathVariable Long userId, @RequestParam(defaultValue = "8") int limit,
                                     Authentication authentication) {
        SecurityUtil.requireSelf(authentication, userId);
        return discoveryService.hiddenGems(userId, limit);
    }

    @GetMapping("/because-you-loved/{userId}")
    public List<MovieDTO> becauseYouLoved(@PathVariable Long userId, @RequestParam(defaultValue = "8") int limit,
                                          Authentication authentication) {
        SecurityUtil.requireSelf(authentication, userId);
        return discoveryService.becauseYouLoved(userId, limit);
    }

    @GetMapping("/similar-dna/{userId}")
    public List<MovieDTO> similarDna(@PathVariable Long userId, @RequestParam(defaultValue = "8") int limit,
                                     Authentication authentication) {
        SecurityUtil.requireSelf(authentication, userId);
        return discoveryService.similarDna(userId, limit);
    }
}
