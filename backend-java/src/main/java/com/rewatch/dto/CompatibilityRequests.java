package com.rewatch.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request body for the anonymous, no-signup compatibility check. */
public class CompatibilityRequests {

    public static class CheckCompatibility {
        @NotBlank private String targetUsername;

        // Capped at the quiz's own size — CompatibilityService.check() further
        // validates each titleId is actually one of the current quiz titles, so
        // this bound is just cheap, early protection against an oversized body,
        // not the real validation.
        @NotEmpty @Size(max = 8)
        @Valid
        private List<Answer> responses;

        public String getTargetUsername() { return targetUsername; }
        public void setTargetUsername(String v) { this.targetUsername = v; }
        public List<Answer> getResponses() { return responses; }
        public void setResponses(List<Answer> v) { this.responses = v; }
    }

    public static class Answer {
        @NotNull private Long titleId;
        @NotNull @Min(1) @Max(5) private Integer overall;

        public Long getTitleId() { return titleId; }
        public void setTitleId(Long v) { this.titleId = v; }
        public Integer getOverall() { return overall; }
        public void setOverall(Integer v) { this.overall = v; }
    }
}
