# 13 - API Specification

## GET `/api/movies`
* **Description:** Retrieves all movies enriched with TasteDNA Match % and explainability text.
* **Response:** Array of Movie JSON objects (`200 OK`).

## POST `/api/rate`
* **Description:** Submits a user rating for a specific movie title.
* **Payload:** `{"movie_id": 1, "rating": 5}`
* **Response:** `{"message": "saved rating"}` (`201 Created`).