import sqlite3
import math
from flask import Flask, jsonify, request
from flask_cors import CORS

app = Flask(__name__)
CORS(app)

dbpath = "../database/rewatch.db"

def getdb():
    conn = sqlite3.connect(dbpath)
    conn.row_factory = sqlite3.Row
    return conn

# Cosine similarity loop without extra libraries
def calculate_similarity(user_dna, movie_dna):
    dot_product = 0.0
    user_sq = 0.0
    movie_sq = 0.0
    
    for i in range(len(user_dna)):
        dot_product += user_dna[i] * movie_dna[i]
        user_sq += user_dna[i] * user_dna[i]
        movie_sq += movie_dna[i] * movie_dna[i]
        
    if user_sq == 0 or movie_sq == 0:
        return 0.0
        
    return dot_product / (math.sqrt(user_sq) * math.sqrt(movie_sq))

# Explainability Generator based on highest matching dimensions
def generate_explanation(user_dna, movie_dna, title):
    labels = [
        "Pacing", "Emotional Weight", "Narrative Complexity", 
        "Visual Atmosphere", "Tone", "Action Density", 
        "Thematic Depth", "Humor Density", "Tension", "Character Focus"
    ]
    
    best_idx = 0
    highest_val = 0
    for i in range(len(movie_dna)):
        if movie_dna[i] > highest_val and user_dna[i] >= 50:
            highest_val = movie_dna[i]
            best_idx = i
            
    reason_label = labels[best_idx]
    return f"Recommended because your TasteDNA aligns with high '{reason_label}' ({movie_dna[best_idx]}/100)."

@app.route("/api/movies", methods=["GET"])
def getmovies():
    conn = getdb()
    user_row = conn.execute("SELECT * FROM users WHERE id = 1").fetchone()
    rows = conn.execute("SELECT * FROM movies").fetchall()
    conn.close()
    
    # Parse default test user DNA
    user_dna = []
    user_dna_str = user_row["dna"].split(",")
    for val in user_dna_str:
        user_dna.append(int(val))
    
    movies = []
    for r in rows:
        arr = r["storyprint"].split(",")
        nums = []
        for x in arr:
            nums.append(int(x))
        
        # Recommendation Algorithm (Similarity score converted to %)
        sim_score = calculate_similarity(user_dna, nums)
        match_pct = round(sim_score * 100, 1)
        if match_pct > 99.0:
            match_pct = 98.5
            
        explanation = generate_explanation(user_dna, nums, r["title"])
        
        movies.append({
            "id": r["id"],
            "title": r["title"],
            "year": r["release_year"],
            "rating": r["rating"],
            "storyprint": nums,
            "match_pct": match_pct,
            "explanation": explanation
        })
        
    return jsonify(movies), 200

@app.route("/api/rate", methods=["POST"])
def ratemovie():
    data = request.get_json()
    if not data or "movie_id" not in data or "rating" not in data:
        return jsonify({"error": "missing movie_id or rating"}), 400

    mid = data["movie_id"]
    val = float(data["rating"])

    if val < 0.5 or val > 5.0:
        return jsonify({"error": "rating must be between 0.5 and 5.0"}), 400

    conn = getdb()
    conn.execute(
        "INSERT INTO watch_logs (user_id, movie_id, rating) VALUES (?, ?, ?)",
        (1, mid, val)
    )
    conn.commit()
    conn.close()

    return jsonify({"message": "saved rating"}), 201

if __name__ == "__main__":
    app.run(port=5000, debug=True)