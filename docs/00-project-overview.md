# 00 - Project Overview: Re:Watch

## Executive Summary
**Re:Watch** is an intelligent content tracking and discovery platform engineered to eliminate decision fatigue in streaming. By replacing static genre tags with multi-dimensional content analysis (**StoryPrint**) and dynamic user preference profiling (**TasteDNA**), Re:Watch delivers explainable, highly targeted recommendations and viewing analytics.

## Why It Exists
Modern streaming consumers face three persistent problems:
1. **Decision Fatigue & Doom Scrolling:** Users spend an average of 15–20 minutes browsing across platforms without selecting content.
2. **Superficial Taxonomy:** Broad genre labels (e.g., "Action" or "Drama") fail to capture tone, pacing, or emotional resonance.
3. **Black-Box Recommendations:** Existing algorithms prioritize platform licensing and popularity bias over genuine user compatibility.

## Target Users
*   **Active Enthusiasts:** Viewers who log, rate, and track their viewing habits across multiple streaming services.
*   **Mood-Driven Viewers:** Users seeking specific emotional or narrative experiences rather than specific actors or franchises.
*   **Data-Curious Consumers:** Users who value visual analytics, personalized statistics, and annual "Wrapped" summaries of their media intake.

## Core Innovation
*   **StoryPrint Model:** A multi-axis attribute scoring system that maps films and series across dimensions like emotional weight, narrative pacing, and thematic density.
*   **TasteDNA:** A weighted, time-decaying user preference model that adapts to evolving tastes.
*   **Insight AI:** A natural-language search and explanation engine that translates user mood queries into StoryPrint parameters and explains *why* a title was recommended.
*   **Re:Watch Wrapped:** Dynamic, publication-ready statistical breakdowns of user viewing history.

## Tech Stack
*   **Frontend:** React / Next.js, TypeScript, Tailwind CSS
*   **Backend:** Node.js / Express (or Python / FastAPI for ML/AI microservices)
*   **Database:** PostgreSQL (Relational Data + TasteDNA) + pgvector (StoryPrint Embeddings)
*   **External APIs:** TMDB API (Metadata, Posters, Credits), LLM Endpoints (Insight AI NLP)

## High-Level Architecture
1. **Client Layer:** Responsive web/PWA interface handling authentication, search, and data visualization.
2. **API Gateway / Core Services:** Manages user CRUD operations, watch history logging, and review aggregations.
3. **Intelligence Layer:** Computes cosine similarity between user `TasteDNA` vectors and content `StoryPrint` vectors to generate ranked feeds.

## Project Timeline
*   **Phase 1:** Core Requirements, Architecture, & StoryPrint/TasteDNA Mathematical Modeling.
*   **Phase 2:** Database Setup, Backend Endpoints, and TMDB Syncing.
*   **Phase 3:** Recommendation Engine Development & AI Prompt Engineering.
*   **Phase 4:** Frontend UI/UX, Dynamic Visualizations, and End-to-End Testing.