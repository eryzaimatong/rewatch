package com.rewatch.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rewatch.model.Report;
import com.rewatch.service.ReportService;

/**
 * Kept separate from ReportController (which any authenticated user can hit)
 * rather than folded into AdminController — that class is specifically
 * lexicon/scoring tooling per its own doc comment, a different concern. Both
 * endpoints here fall under SecurityConfig's route-level hasRole("ADMIN")
 * gate on /api/admin/**, no per-method check needed. No frontend for this
 * yet — same as AdminController's existing endpoints, which are curl/API-only.
 */
@RestController
@RequestMapping("/api/admin/reports")
public class AdminReportController {

    private final ReportService reportService;

    public AdminReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping
    public List<Report> listOpen() {
        return reportService.listOpen();
    }

    @PostMapping("/{id}/resolve")
    public Map<String, Object> resolve(@PathVariable Long id) {
        reportService.resolve(id);
        return Map.of("status", "success");
    }
}
