package com.rewatch.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.rewatch.dto.ReportRequest;
import com.rewatch.model.Report;
import com.rewatch.security.RateLimiterService;
import com.rewatch.service.ReportService;

/**
 * file() had no rate limit at all until now — nothing bounded how many
 * duplicate/spam reports a single account could flood the moderation queue
 * with. See SocialControllerTest's class comment for why ReportService is a
 * hand-written subclass rather than @Mock.
 */
class ReportControllerTest {

    private static class FakeReportService extends ReportService {
        int fileCalls = 0;

        FakeReportService() { super(null, null, null); }

        @Override
        public Report file(Long reporterId, Long reportedUserId, Report.Reason reason, String details, Long commentId) {
            fileCalls++;
            return new Report(reporterId, reportedUserId, reason, details, Instant.now());
        }
    }

    private Authentication authAs(long userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, java.util.List.of());
    }

    private ReportRequest request(long reportedUserId) {
        ReportRequest req = new ReportRequest();
        req.setReportedUserId(reportedUserId);
        req.setReason(Report.Reason.SPAM);
        return req;
    }

    @Test
    void fileIsRateLimitedAfterTooManyReportsInAnHour() {
        FakeReportService reportService = new FakeReportService();
        ReportController controller = new ReportController(reportService, new RateLimiterService());

        Authentication auth = authAs(1L);
        ResponseEntity<?> last = null;
        // MAX_REPORTS_PER_HOUR is 10 — the 11th call must be rejected
        // before ever reaching ReportService.file.
        for (int i = 0; i < 11; i++) {
            last = controller.file(request(200L + i), auth);
        }

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, last.getStatusCode());
        assertEquals(10, reportService.fileCalls, "the 11th call should never reach the service");
    }

    @Test
    void fileSucceedsNormallyUnderTheLimit() {
        FakeReportService reportService = new FakeReportService();
        ReportController controller = new ReportController(reportService, new RateLimiterService());

        ResponseEntity<?> result = controller.file(request(200L), authAs(1L));

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(1, reportService.fileCalls);
    }
}
