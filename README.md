# 🎬 Re:Watch

> Discover stories that understand you.

---

## Overview

Re:Watch is an AI-powered entertainment discovery platform that recommends movies, TV shows, anime, K-dramas, and books based on emotions, storytelling, cinematic style, and personal taste—not just genres.

Instead of asking:

"What genre do you like?"

Re:Watch asks:

"How do you want to feel?"

---

## Why Re:Watch?

Choosing something to watch has become frustrating.

People spend more time scrolling than watching.

Current recommendation systems prioritize popularity and engagement rather than emotional connection.

Re:Watch solves this by building a personalized Taste DNA and Story Fingerprint Engine that understands why users enjoy certain stories.

---

## Project Status

🟢 Working end-to-end — real auth, real recommendations, real evolving taste
profiles. See [`docs/CASE-STUDY.md`](docs/CASE-STUDY.md) for the engineering
detail: how a movie's emotional vector is derived, why the match score's
explanation is guaranteed to add up, why the taste profile is replayed from
a rating log rather than mutated in place, and an honest list of what's
still limited.

---

## Tech Stack

Frontend
- React (Vite), plain CSS (no framework), `react-router-dom`, `framer-motion`

Backend
- Spring Boot 3 / Java 17

Database
- PostgreSQL

Authentication
- BCrypt password hashing + stateless JWT sessions (`spring-boot-starter-security`)

Movie Data
- TMDB API (genres + keywords, hand-authored lexicon — no LLM in the recommendation path)

---

## Core Features

- Mood Search
- Taste DNA
- Story Fingerprint
- Explainable Recommendations
- Watch Together
- Monthly Wrapped
- Taste Evolution

---

## Folder Structure

(We'll update this later.)

---

## Contributors

Eryza Mae C. Imatong
