package com.rewatch.controller;

import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rewatch.dto.ReportRequest;
import com.rewatch.security.SecurityUtil;
import com.rewatch.service.ReportService;

/** File a report against another user. Any authenticated user; reporterId always comes from the JWT. */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping
    public ResponseEntity<?> file(@Valid @RequestBody ReportRequest req, Authentication authentication) {
        Long reporterId = SecurityUtil.currentUserId(authentication);
        if (reporterId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("status", "error", "message", "Login required"));
        }
        try {
            reportService.file(reporterId, req.getReportedUserId(), req.getReason(), req.getDetails(), req.getCommentId());
            return ResponseEntity.ok(Map.of("status", "success", "message", "Report filed."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }
}
