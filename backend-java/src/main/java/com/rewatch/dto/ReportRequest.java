package com.rewatch.dto;

import jakarta.validation.constraints.NotNull;

import com.rewatch.model.Report;

/** Body for POST /api/reports. reporterId is never part of this — it comes from the caller's own JWT. */
public class ReportRequest {

    @NotNull private Long reportedUserId;
    @NotNull private Report.Reason reason;
    private String details;

    public Long getReportedUserId() { return reportedUserId; }
    public void setReportedUserId(Long v) { this.reportedUserId = v; }
    public Report.Reason getReason() { return reason; }
    public void setReason(Report.Reason v) { this.reason = v; }
    public String getDetails() { return details; }
    public void setDetails(String v) { this.details = v; }
}
