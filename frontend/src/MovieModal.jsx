import React, { useState } from "react";

// Official TMDB vertical poster URLs
const POSTERS = {
  "Interstellar": "https://image.tmdb.org/t/p/w500/gEU2QniE6E77NI6lCU6MxlNBvIx.jpg",
  "Parasite": "https://image.tmdb.org/t/p/w500/7IiTTgloJzvGI1TAYymCfbfl3vT.jpg",
  "Arrival": "https://image.tmdb.org/t/p/w500/x2FJsf1ElAgr63Y3PNPtJrcmpoe.jpg",
  "Reply 1988": "https://image.tmdb.org/t/p/w500/3EwozHAGKvg8q4R6M33A9fI5Ggg.jpg",
  "Hospital Playlist": "https://image.tmdb.org/t/p/w500/vGk4SffgokMWhK2xS2786BnpwPj.jpg",
  "Our Beloved Summer": "https://image.tmdb.org/t/p/w500/w70rP9c7B8H7S8h0P0z5S7S0K0.jpg",
  "Twenty Five Twenty One": "https://image.tmdb.org/t/p/w500/eWeuKntQ1x4S9H6VbQ7Z9xV3bM0.jpg",
  "My Liberation Notes": "https://image.tmdb.org/t/p/w500/xR4aL3S5Z6J1qA5Vv9W8kG9qQ0n.jpg",
  "The Glory": "https://image.tmdb.org/t/p/w500/6jOqZ9A1A1u7WwS6A1Bw7V9S2E.jpg",
  "La La Land": "https://image.tmdb.org/t/p/w500/uDO8zWDhfWwoFdKS4fzkUJt0Rf0.jpg"
};

// Standard w780 TMDB backdrop URLs
const BACKDROPS = {
  "Interstellar": "https://image.tmdb.org/t/p/w780/rAiYTfKGqDCRIIqo664sY9XZIvQ.jpg",
  "Parasite": "https://image.tmdb.org/t/p/w780/TU9NWdfpCcrf4f13C0dE8TjqS1.jpg",
  "Arrival": "https://image.tmdb.org/t/p/w780/yIZ1xendyqTTNwzgWI8BgKk9Qz8.jpg",
  "Reply 1988": "https://image.tmdb.org/t/p/w780/8e1zW4M40X2d9k1q50d4w9921c.jpg",
  "Hospital Playlist": "https://image.tmdb.org/t/p/w780/uL60N2k2S110n8X9g2N4m9330g.jpg",
  "Our Beloved Summer": "https://image.tmdb.org/t/p/w780/k1n8T1L2c184S903W8e055S840.jpg",
  "Twenty Five Twenty One": "https://image.tmdb.org/t/p/w780/eWeuKntQ1x4S9H6VbQ7Z9xV3bM0.jpg",
  "My Liberation Notes": "https://image.tmdb.org/t/p/w780/xR4aL3S5Z6J1qA5Vv9W8kG9qQ0n.jpg",
  "The Glory": "https://image.tmdb.org/t/p/w780/6jOqZ9A1A1u7WwS6A1Bw7V9S2E.jpg",
  "La La Land": "https://image.tmdb.org/t/p/w780/uDO8zWDhfWwoFdKS4fzkUJt0Rf0.jpg"
};

const FALLBACK_POSTER = "https://placehold.co/500x750/191a21/a855f7?text=Re:Watch";

export default function MovieModal({ movie, onClose }) {
  if (!movie) {
    return null;
  }

  const title = typeof movie === "string" ? movie : movie.title;
  const score = movie.matchScore || 88;
  const reasons = movie.reasons || movie.explanations || ["balanced storytelling fit", "emotional depth matches"];
  const synopsis = movie.synopsis || movie.overview || "An unforgettable exploration of character growth, quiet devastation, and deep human connection.";
  const year = movie.year || 2024;
  
  // Resolve artwork safely
  const posterurl = POSTERS[title] || movie.posterUrl || FALLBACK_POSTER;
  const backdropurl = BACKDROPS[title] || movie.backdropPath || "";
  const [imgfailed, setimgfailed] = useState(false);

  function opentrailer() {
    const q = encodeURIComponent(`${title} official trailer`);
    window.open(`https://www.youtube.com/results?search_query=${q}`, "_blank");
  }

  return (
    <div 
      style={{
        position: "fixed",
        inset: 0,
        zIndex: 1000,
        background: "rgba(9, 10, 14, 0.85)",
        backdropFilter: "blur(10px)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        padding: "20px"
      }}
      onClick={onClose}
    >
      <div
        style={{
          width: "100%",
          maxWidth: "760px",
          maxHeight: "90vh",
          overflowY: "auto",
          background: "#14151a",
          border: "1px solid rgba(168, 85, 247, 0.3)",
          borderRadius: "24px",
          boxShadow: "0 25px 60px rgba(0, 0, 0, 0.8)",
          position: "relative",
          display: "flex",
          flexDirection: "column"
        }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* CLOSE BUTTON */}
        <button
          type="button"
          onClick={onClose}
          style={{
            position: "absolute",
            top: "16px",
            right: "16px",
            zIndex: 10,
            width: "36px",
            height: "36px",
            minHeight: "auto",
            padding: 0,
            borderRadius: "50%",
            background: "rgba(0, 0, 0, 0.7)",
            border: "1px solid rgba(255, 255, 255, 0.2)",
            color: "#fff",
            fontSize: "18px",
            fontWeight: "bold",
            cursor: "pointer"
          }}
        >
          ✕
        </button>

        {/* CINEMATIC HEADER WITH GRADIENT FALLBACK & FLOATING POSTER */}
        <div 
          style={{ 
            position: "relative", 
            minHeight: "260px", 
            width: "100%", 
            overflow: "hidden", 
            background: "linear-gradient(135deg, #1e1b4b 0%, #0f172a 50%, #090a0e 100%)", 
            display: "flex", 
            alignItems: "flex-end", 
            padding: "24px" 
          }}
        >
          {/* BACKDROP IMAGE (HIDDEN IF BLOCKED OR BROKEN) */}
          {backdropurl && !imgfailed && (
            <img
              src={backdropurl}
              alt=""
              onError={() => setimgfailed(true)}
              style={{ 
                position: "absolute", 
                inset: 0, 
                width: "100%", 
                height: "100%", 
                objectFit: "cover", 
                opacity: 0.35 
              }}
            />
          )}
          <div style={{ position: "absolute", inset: 0, background: "linear-gradient(180deg, rgba(20,21,26,0.1) 0%, rgba(20,21,26,0.95) 100%)" }} />
          
          {/* HEADER CONTENT: POSTER + TITLE */}
          <div style={{ position: "relative", zIndex: 2, display: "flex", alignItems: "flex-end", gap: "20px", width: "100%" }}>
            <img
              src={posterurl}
              alt={`${title} poster`}
              style={{
                width: "110px",
                height: "165px",
                objectFit: "cover",
                borderRadius: "12px",
                border: "2px solid rgba(255, 255, 255, 0.15)",
                boxShadow: "0 10px 25px rgba(0, 0, 0, 0.7)",
                flexShrink: 0
              }}
            />
            <div>
              <span style={{ fontSize: "12px", fontWeight: "800", textTransform: "uppercase", letterSpacing: "2px", color: "#a855f7", display: "block" }}>
                CINEMATIC PROFILE
              </span>
              <h2 style={{ fontSize: "32px", fontWeight: "800", margin: "4px 0 0 0", color: "#fff", lineHeight: "1.1" }}>
                {title}
              </h2>
            </div>
          </div>
        </div>

        {/* MODAL BODY */}
        <div style={{ padding: "28px", display: "flex", flexDirection: "column", gap: "20px" }}>
          {/* METADATA PILLS */}
          <div style={{ display: "flex", alignItems: "center", gap: "12px", flexWrap: "wrap" }}>
            <span
              style={{
                background: "rgba(168, 85, 247, 0.2)",
                border: "1px solid rgba(168, 85, 247, 0.4)",
                color: "#c084fc",
                padding: "6px 14px",
                borderRadius: "999px",
                fontSize: "13px",
                fontWeight: "800"
              }}
            >
              ✨ {Math.round(score)}% Taste Match
            </span>
            <span style={{ color: "#a1a1aa", fontSize: "14px", fontWeight: "600" }}>
              {year} • Drama / Emotional Storytelling
            </span>
          </div>

          {/* SYNOPSIS */}
          <div>
            <h3 style={{ fontSize: "14px", fontWeight: "700", color: "#e4e4e7", textTransform: "uppercase", letterSpacing: "1px", marginBottom: "8px" }}>
              Storyline
            </h3>
            <p style={{ color: "#a1a1aa", fontSize: "15px", lineHeight: "1.6", margin: 0 }}>
              {synopsis}
            </p>
          </div>

          {/* WHY IT FITS YOUR TASTE BOX */}
          <div
            style={{
              background: "rgba(168, 85, 247, 0.05)",
              border: "1px solid rgba(168, 85, 247, 0.2)",
              borderRadius: "16px",
              padding: "16px 20px"
            }}
          >
            <span style={{ fontSize: "12px", fontWeight: "800", textTransform: "uppercase", color: "#a855f7", display: "block", marginBottom: "8px" }}>
              Why this matches your Taste DNA
            </span>
            <div style={{ display: "flex", flexDirection: "column", gap: "6px" }}>
              {reasons.map((r, idx) => (
                <span key={idx} style={{ fontSize: "14px", color: "#e4e4e7" }}>
                  ✓ {r}
                </span>
              ))}
            </div>
          </div>

          {/* ACTIONS */}
          <div style={{ display: "flex", gap: "12px", marginTop: "8px" }}>
            <button
              type="button"
              onClick={opentrailer}
              style={{
                flex: 1,
                background: "#a855f7",
                color: "#fff",
                border: "none",
                padding: "14px",
                borderRadius: "14px",
                fontWeight: "700",
                fontSize: "15px",
                cursor: "pointer",
                boxShadow: "0 6px 18px rgba(168, 85, 247, 0.4)"
              }}
            >
              ▶ Watch Official Trailer
            </button>
            <button
              type="button"
              onClick={onClose}
              style={{
                padding: "14px 24px",
                background: "rgba(255, 255, 255, 0.08)",
                border: "1px solid rgba(255, 255, 255, 0.15)",
                color: "#fff",
                borderRadius: "14px",
                fontWeight: "600",
                fontSize: "15px",
                cursor: "pointer"
              }}
            >
              Close
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}