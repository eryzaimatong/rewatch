# 18 - Testing Strategy

## Local Verification Checklist
1. **Database Setup:** Verify `rewatch.db` generates cleanly via `python init_db.py`.
2. **API Accuracy:** Test GET `/api/movies` to ensure cosine similarity returns values between `0.0` and `100.0`.
3. **Frontend Integration:** Ensure star buttons trigger POST `/api/rate` and display success confirmation alerts.