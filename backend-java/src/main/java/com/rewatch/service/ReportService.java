package com.rewatch.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rewatch.model.Report;
import com.rewatch.repository.ReportRepository;
import com.rewatch.repository.UserRepository;

@Service
public class ReportService {

    private final ReportRepository reportRepo;
    private final UserRepository userRepo;

    public ReportService(ReportRepository reportRepo, UserRepository userRepo) {
        this.reportRepo = reportRepo;
        this.userRepo = userRepo;
    }

    @Transactional
    public Report file(Long reporterId, Long reportedUserId, Report.Reason reason, String details) {
        if (reporterId.equals(reportedUserId)) {
            throw new IllegalArgumentException("Cannot report yourself");
        }
        if (userRepo.findById(reportedUserId).isEmpty()) {
            throw new IllegalArgumentException("Unknown user " + reportedUserId);
        }
        if (reason == null) {
            throw new IllegalArgumentException("A reason is required");
        }
        return reportRepo.save(new Report(reporterId, reportedUserId, reason, details, Instant.now()));
    }

    public List<Report> listOpen() {
        return reportRepo.findByStatusOrderByCreatedAtDesc(Report.Status.OPEN);
    }

    @Transactional
    public void resolve(Long reportId) {
        reportRepo.findById(reportId).ifPresent(r -> {
            r.setStatus(Report.Status.REVIEWED);
            reportRepo.save(r);
        });
    }
}
