# 04 - Feature Specifications

## 1. Mood Search (Natural Language AI Discovery)
*   **Purpose:** Allow users to discover content using conversational, emotional, or situational descriptions rather than strict title/genre filters.
*   **Priority:** High (Core Differentiator)
*   **Dependencies:** Insight AI (LLM Service), TMDB API, pgvector Database
*   **Inputs:** 
    *   Natural language text string (e.g., *"A visually stunning sci-fi movie that makes me feel existentially small but hopeful"*).
    *   User ID (to inject TasteDNA context).
*   **Outputs:** 
    *   Ranked list of 10 movie/series objects.
    *   Match percentage score per item.
    *   AI-generated justification text per item.
*   **Future Improvements:** Voice-to-text input; multi-turn conversation refinement (*"Make it slightly faster paced than those"*).
*   **Example Output:**
    *   *Title:* Arrival (2016)
    *   *Match:* 96%
    *   *Why:* "Matches your desire for high thematic complexity and hopeful existential sci-fi, aligning with your TasteDNA preference for contemplative pacing."

---

## 2. TasteDNA Evolution Engine
*   **Purpose:** Maintain an accurate, time-weighted mathematical model of user media preferences.
*   **Priority:** Critical
*   **Dependencies:** Database Design (Users, Ratings, Logs tables), Recommendation Engine
*   **Inputs:**
    *   User action: Log, Rate, Review, or Bookmark.
    *   Content StoryPrint vector ($V_{item}$).
    *   Timestamp of action ($T$).
*   **Outputs:**
    *   Updated 10-dimensional TasteDNA user vector ($V_{user}$).
*   **Future Improvements:** User-facing sliders to manually adjust or freeze certain DNA attributes.
*   **Example:** Rating *Mad Max: Fury Road* a 5/5 immediately increases the user's `Pacing: Kinetic` and `Tone: Gritty` weights, subject to a 180-day decay curve.

---

## 3. Re:Watch Wrapped (Annual Retrospective)
*   **Purpose:** Boost user engagement and social sharing by providing an aesthetic, data-rich summary of their yearly viewing habits.
*   **Priority:** Medium
*   **Dependencies:** Analytics Engine, Watch Logs Table, UI Canvas/Share Generation
*   **Inputs:** User ID, Date Range (Jan 1 – Dec 31).
*   **Outputs:** 
    *   Interactive multi-story visual sequence.
    *   Exportable social media cards (PNG/IG Story format).
*   **Future Improvements:** Personalized AI audio narration of the user's yearly stats.
*   **Example Metrics Highlighted:** Total Minutes Watched, Top 3 StoryPrint Dimensions, "Obscurity Score" (how niche their tastes were compared to the platform average).