package com.rewatch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rewatch.model.Report;
import com.rewatch.model.ReviewComment;
import com.rewatch.model.User;
import com.rewatch.repository.ReportRepository;
import com.rewatch.repository.ReviewCommentRepository;
import com.rewatch.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock private ReportRepository reportRepo;
    @Mock private UserRepository userRepo;
    @Mock private ReviewCommentRepository reviewCommentRepo;

    private ReportService newService() {
        return new ReportService(reportRepo, userRepo, reviewCommentRepo);
    }

    @Test
    void commentReportThrowsWhenTheCommentBelongsToSomeoneElse() {
        when(userRepo.findById(2L)).thenReturn(Optional.of(new User()));
        ReviewComment comment = new ReviewComment(10L, 3L, "hi", Instant.now());
        when(reviewCommentRepo.findById(99L)).thenReturn(Optional.of(comment));

        assertThrows(IllegalArgumentException.class,
                () -> newService().file(1L, 2L, Report.Reason.SPAM, null, 99L));
    }

    @Test
    void commentReportSucceedsAndCarriesTheCommentId() {
        when(userRepo.findById(2L)).thenReturn(Optional.of(new User()));
        ReviewComment comment = new ReviewComment(10L, 2L, "spam link", Instant.now());
        when(reviewCommentRepo.findById(99L)).thenReturn(Optional.of(comment));
        when(reportRepo.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));

        Report saved = newService().file(1L, 2L, Report.Reason.SPAM, "looks like spam", 99L);

        assertEquals(99L, saved.getCommentId());
        assertEquals(2L, saved.getReportedUserId());
    }

    @Test
    void profileLevelReportStillWorksWithNoCommentId() {
        when(userRepo.findById(2L)).thenReturn(Optional.of(new User()));
        when(reportRepo.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));

        Report saved = newService().file(1L, 2L, Report.Reason.HARASSMENT, "unrelated to any comment");

        assertEquals(null, saved.getCommentId());
    }
}
