package com.rewatch.dto;

import com.rewatch.model.Title;

/**
 * The exact fields Onboarding.jsx's favourites picker renders — id (React
 * key), title (display + matching), poster (thumbnail), type (tab
 * bucketing), popularity (sort order) — confirmed against the component,
 * not guessed. Everything else on Title (synopsis, all 10 trait doubles,
 * keywordNames, etc.) is what made GET /api/titles' full-entity response
 * 3,014 KB to render a 60-title picker page.
 */
public record TitlePickerDTO(Long id, String title, String poster, String type, double popularity) {

    public static TitlePickerDTO from(Title t) {
        return new TitlePickerDTO(t.getId(), t.getTitle(), t.getPoster(), t.getType(), t.getPopularity());
    }
}
