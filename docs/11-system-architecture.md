# 11 - System Architecture

## Three-Tier Architecture
1. **Frontend Layer:** Single-page React application powered by Vite (`frontend/`).
2. **API & Engine Layer:** Lightweight Python Flask REST API (`backend/app.py`).
3. **Data Layer:** Relational SQLite database (`database/rewatch.db`).

## Data Flow
`React Client` --(HTTP GET /api/movies)--> `Flask API` --> `SQLite DB`
`Flask API` <-- (Cosine Sim & Explainability Calc) -- `Data Rows`
`React Client` <-- (JSON Feed with Match %) -- `Flask API`