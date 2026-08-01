# 10 - Insight AI & Explainability Generator

## Purpose
Provides transparent reasoning for every recommended title in the discovery feed.

## Execution Logic
1. Scans the movie's 10-dimensional StoryPrint vector.
2. Identifies the highest-scoring attribute where the user's TasteDNA score is `>= 50`.
3. Synthesizes an explainability string highlighting that specific narrative trait (e.g., *Thematic Depth* or *Visual Atmosphere*).