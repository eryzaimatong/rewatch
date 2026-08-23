package com.rewatch.dto;

import jakarta.validation.constraints.NotNull;

public class DailyGuessRequest {
    @NotNull private Long userId;
    @NotNull private Long titleId;

    public Long getUserId() { return userId; }
    public void setUserId(Long v) { this.userId = v; }
    public Long getTitleId() { return titleId; }
    public void setTitleId(Long v) { this.titleId = v; }
}
