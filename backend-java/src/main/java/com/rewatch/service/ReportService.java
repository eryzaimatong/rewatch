package com.rewatch.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rewatch.model.Report;
import com.rewatch.model.ReviewComment;
import com.rewatch.repository.ReportRepository;
import com.rewatch.repository.ReviewCommentRepository;
import com.rewatch.repository.UserRepository;

@Service
public class ReportService {

    private final ReportRepository reportRepo;
    private final UserRepository userRepo;
    private final ReviewCommentRepository reviewCommentRepo;

    public ReportService(ReportRepository reportRepo, UserRepository userRepo,
                         ReviewCommentRepository reviewCommentRepo) {
        this.reportRepo = reportRepo;
        this.userRepo = userRepo;
        this.reviewCommentRepo = reviewCommentRepo;
    }

    @Transactional
    public Report file(Long reporterId, Long reportedUserId, Report.Reason reason, String details) {
        return file(reporterId, reportedUserId, reason, details, null);
    }

    @Transactional
    public Report file(Long reporterId, Long reportedUserId, Report.Reason reason, String details, Long commentId) {
        if (reporterId.equals(reportedUserId)) {
            throw new IllegalArgumentException("Cannot report yourself");
        }
        if (userRepo.findById(reportedUserId).isEmpty()) {
            throw new IllegalArgumentException("Unknown user " + reportedUserId);
        }
        if (reason == null) {
            throw new IllegalArgumentException("A reason is required");
        }
        if (commentId != null) {
            ReviewComment comment = reviewCommentRepo.findById(commentId)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown comment " + commentId));
            if (!comment.getAuthorUserId().equals(reportedUserId)) {
                throw new IllegalArgumentException("That comment wasn't written by the reported user.");
            }
        }
        Report report = new Report(reporterId, reportedUserId, reason, details, Instant.now());
        report.setCommentId(commentId);
        return reportRepo.save(report);
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
