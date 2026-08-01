# 03 - Product Requirements Document (PRD)

## 1. Authentication & Account Management
*   **REQ-AUTH-01:** Users must be able to register and log in using Email/Password, Google OAuth, and Apple ID.
*   **REQ-AUTH-02:** System must use JSON Web Tokens (JWT) for secure, stateless session management.
*   **REQ-AUTH-03:** Users must be able to reset passwords via email verification.

## 2. Search & Discovery
*   **REQ-SRCH-01:** Basic search must query metadata (title, actor, director, year) with autocomplete via TMDB API integration.
*   **REQ-SRCH-02:** Advanced search must allow filtering by specific StoryPrint attribute ranges (e.g., Tone > 70, Pacing < 40).

## 3. Reviews & Ratings
*   **REQ-REV-01:** Users can rate titles on a 0.5 to 5.0 star scale (or 1–100 numerical score).
*   **REQ-REV-02:** Users can log a title with a specific watch date, rewatch toggle, and markdown-supported written review.
*   **REQ-REV-03:** Reviews can be marked as containing spoilers, requiring a click-to-reveal interaction.

## 4. Recommendation Engine
*   **REQ-REC-01:** System must calculate a personalized "Match Score" (0–100%) for every unwatched title using TasteDNA and StoryPrint cosine similarity.
*   **REQ-REC-02:** Cold-start users (fewer than 5 ratings) must be presented with an onboarding visual selector to establish baseline TasteDNA.

## 5. TasteDNA & StoryPrint
*   **REQ-DNA-01:** System must dynamically update a user's TasteDNA vector upon any rating or log event.
*   **REQ-DNA-02:** TasteDNA calculations must incorporate a half-life time-decay function so recent ratings impact the profile more heavily than older ones.

## 6. Insight AI
*   **REQ-AI-01:** Users can execute natural language mood queries (e.g., *"Show me tense, slow-burn psychological thrillers"*).
*   **REQ-AI-02:** Insight AI must output a 2-sentence explanation for why a movie is recommended based on the user's TasteDNA.

## 7. Statistics & Wrapped
*   **REQ-STAT-01:** Users have access to a real-time dashboard displaying total hours watched, top directors, actors, and genre distribution.
*   **REQ-STAT-02:** System must generate an annual, shareable "Re:Watch Wrapped" visual summary every December.

## 8. Profile, Settings, & Social
*   **REQ-PROF-01:** Users can create custom, shareable watchlists.
*   **REQ-PROF-02:** Users can follow other users to view an activity feed of recent logs, ratings, and reviews.
*   **REQ-PROF-03:** Users can toggle account visibility between Public and Private.