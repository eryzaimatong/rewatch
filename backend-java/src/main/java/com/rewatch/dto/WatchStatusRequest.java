package com.rewatch.dto;

import jakarta.validation.constraints.NotNull;

/** status of null/blank clears the row — see WatchStatusService.setStatus. */
public class WatchStatusRequest {
    @NotNull private Long userId;
    @NotNull private Long titleId;
    private String status;

    public Long getUserId() { return userId; }
    public void setUserId(Long v) { this.userId = v; }
    public Long getTitleId() { return titleId; }
    public void setTitleId(Long v) { this.titleId = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
}
