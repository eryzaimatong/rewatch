import { useState, useEffect } from "react";
import { gettastedna, savetastedna } from "./api";

export default function TasteDNA() {
  const [comfort, setcomfort] = useState(50);
  const [romance, setromance] = useState(50);
  const [slowburn, setslowburn] = useState(50);
  const [nostalgia, setnostalgia] = useState(50);
  const [sadness, setsadness] = useState(50);
  const [saved, setsaved] = useState(false);

  useEffect(() => {
    loadprofile();
  }, []);

  async function loadprofile() {
    const id = localStorage.getItem("userId");
    if (!id) {
      return;
    }
    const data = await gettastedna(id);
    if (data) {
      if (data.comfort !== undefined) setcomfort(data.comfort);
      if (data.romance !== undefined) setromance(data.romance);
      if (data.slowBurn !== undefined || data.slowburn !== undefined) {
        setslowburn(data.slowBurn !== undefined ? data.slowBurn : data.slowburn);
      }
      if (data.nostalgia !== undefined) setnostalgia(data.nostalgia);
      if (data.sadness !== undefined) setsadness(data.sadness);
    }
  }

  async function handlesave(e) {
    e.preventDefault();
    const id = localStorage.getItem("userId");
    if (!id) {
      alert("login first bro");
      return;
    }
    const profile = { comfort, romance, slowburn, nostalgia, sadness };
    const ok = await savetastedna(id, profile);
    if (ok) {
      setsaved(true);
      setTimeout(() => setsaved(false), 3000);
    } else {
      alert("saving dna failed bro");
    }
  }

  function getpersona() {
    if (sadness > 70 && slowburn > 60) {
      return {
        title: "🌙 Night Thinker",
        desc: "You crave bittersweet endings, quiet devastation, and stories that linger in your chest at 2 AM.",
        match: "Reply 1988 • Our Beloved Summer • Twenty Five Twenty One",
      };
    }
    if (comfort > 70 && romance > 60) {
      return {
        title: "✨ Cozy Romantic",
        desc: "You want found family, warm cinematography, and stories that feel like a soft blanket on a rainy day.",
        match: "Hospital Playlist • Weightlifting Fairy • Hometown Cha-Cha-Cha",
      };
    }
    if (slowburn > 75) {
      return {
        title: "⏳ Slow Burn Connoisseur",
        desc: "You love tension, character growth, and payoffs that take 10 episodes to earn.",
        match: "My Liberation Notes • Interstellar • Arrival",
      };
    }
    if (nostalgia > 70) {
      return {
        title: "📼 Memory Collector",
        desc: "You are drawn to coming-of-age journeys, past eras, and looking back at how time changes people.",
        match: "Reply 1988 • Twenty Five Twenty One • La La Land",
      };
    }
    return {
      title: "🌧️ Emotional Storyteller",
      desc: "You appreciate balanced storytelling with authentic characters, real stakes, and emotional honesty.",
      match: "Parasite • Interstellar • Hospital Playlist",
    };
  }

  const persona = getpersona();

  return (
    <div>
      <h2>your taste dna profile.</h2>
      <p style={{ color: "#a1a1aa", marginTop: "-12px", marginBottom: "24px" }}>
        adjust your traits below to evolve your storytelling personality.
      </p>

      <div
        style={{
          background: "linear-gradient(135deg, rgba(99, 102, 241, 0.15) 0%, rgba(168, 85, 247, 0.15) 100%)",
          border: "1px solid rgba(168, 85, 247, 0.3)",
          padding: "24px",
          borderRadius: "20px",
          marginBottom: "30px",
        }}
      >
        <span
          style={{
            fontSize: "11px",
            fontWeight: "800",
            textTransform: "uppercase",
            letterSpacing: "1.5px",
            color: "#a855f7",
          }}
        >
          your storytelling archetype
        </span>
        <h3 style={{ fontSize: "28px", margin: "8px 0 10px 0" }}>{persona.title}</h3>
        <p style={{ color: "#e4e4e7", fontSize: "15px", margin: "0 0 16px 0" }}>{persona.desc}</p>
        <div
          style={{
            background: "rgba(11, 12, 16, 0.6)",
            padding: "10px 14px",
            borderRadius: "10px",
            fontSize: "13px",
            color: "#a1a1aa",
          }}
        >
          <strong style={{ color: "#fff" }}>Closest Vibe Matches: </strong> {persona.match}
        </div>
      </div>

      <form onSubmit={handlesave}>
        <div style={{ marginBottom: "18px" }}>
          <div style={{ display: "flex", justifyContent: "space-between" }}>
            <label style={{ fontWeight: "700" }}>Comfort & Healing</label>
            <span style={{ color: "#a855f7", fontWeight: "800" }}>{comfort}%</span>
          </div>
          <input
            type="range"
            min="0"
            max="100"
            value={comfort}
            onChange={(e) => setcomfort(Number(e.target.value))}
          />
        </div>

        <div style={{ marginBottom: "18px" }}>
          <div style={{ display: "flex", justifyContent: "space-between" }}>
            <label style={{ fontWeight: "700" }}>Romance & Chemistry</label>
            <span style={{ color: "#a855f7", fontWeight: "800" }}>{romance}%</span>
          </div>
          <input
            type="range"
            min="0"
            max="100"
            value={romance}
            onChange={(e) => setromance(Number(e.target.value))}
          />
        </div>

        <div style={{ marginBottom: "18px" }}>
          <div style={{ display: "flex", justifyContent: "space-between" }}>
            <label style={{ fontWeight: "700" }}>Slow Burn Pacing</label>
            <span style={{ color: "#a855f7", fontWeight: "800" }}>{slowburn}%</span>
          </div>
          <input
            type="range"
            min="0"
            max="100"
            value={slowburn}
            onChange={(e) => setslowburn(Number(e.target.value))}
          />
        </div>

        <div style={{ marginBottom: "18px" }}>
          <div style={{ display: "flex", justifyContent: "space-between" }}>
            <label style={{ fontWeight: "700" }}>Nostalgia & Coming of Age</label>
            <span style={{ color: "#a855f7", fontWeight: "800" }}>{nostalgia}%</span>
          </div>
          <input
            type="range"
            min="0"
            max="100"
            value={nostalgia}
            onChange={(e) => setnostalgia(Number(e.target.value))}
          />
        </div>

        <div style={{ marginBottom: "24px" }}>
          <div style={{ display: "flex", justifyContent: "space-between" }}>
            <label style={{ fontWeight: "700" }}>Bittersweet & Sadness</label>
            <span style={{ color: "#a855f7", fontWeight: "800" }}>{sadness}%</span>
          </div>
          <input
            type="range"
            min="0"
            max="100"
            value={sadness}
            onChange={(e) => setsadness(Number(e.target.value))}
          />
        </div>

        <button type="submit">save taste dna</button>
        {saved && (
          <span style={{ marginLeft: "14px", color: "#4ade80", fontWeight: "700" }}>
            ✓ profile updated!
          </span>
        )}
      </form>
    </div>
  );
}