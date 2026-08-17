import { useState } from "react";
import useModalA11y from "./useModalA11y";
import BrandMark from "./BrandMark";
import {
  CARD_SCALE, CARD_W, CARD_H, drawCardFrame, drawCardFooter, loadImage, shareOrDownloadBlob
} from "./shareCard";
import "./App.css";

const TMDB_IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w500";
const FALLBACK_POSTER = "https://placehold.co/500x750/191a21/a855f7?text=Re:Watch";

function posterUrlFor(movie) {
  if (typeof movie.posterUrl === "string" && movie.posterUrl.startsWith("http")) {
    return movie.posterUrl;
  }
  if (typeof movie.posterPath === "string" && movie.posterPath.trim()) {
    const path = movie.posterPath.startsWith("/") ? movie.posterPath : `/${movie.posterPath}`;
    return `${TMDB_IMAGE_BASE_URL}${path}`;
  }
  return FALLBACK_POSTER;
}

/**
 * The most shareable unit this app produces isn't a monthly recap or a
 * static TasteDNA card — it's "here's how much THIS specific movie matches
 * me, and why," attached to a poster people already recognize from
 * everywhere else online. A recap only exists once a month; this exists
 * every time someone opens a title, so it's the higher-frequency share
 * loop. Same 9:16 canvas-export pattern as Wrapped/TasteProfile (see
 * shareCard.js) — poster art is the visual hook, the match score is the
 * payoff, the driver reasons are the receipts.
 */
export default function MatchShareCard({ movie, matchScore, drivers, username, onClose }) {
  const modalRef = useModalA11y(onClose);
  const [exporting, setexporting] = useState(false);
  const [shareErr, setshareErr] = useState("");

  const topDrivers = (drivers || []).slice(0, 3);
  const roundedScore = Math.round(matchScore ?? 0);

  async function exportCard() {
    setexporting(true);
    setshareErr("");

    const posterImg = await loadImage(posterUrlFor(movie)).catch(() => null);

    const canvas = document.createElement("canvas");
    canvas.width = CARD_W * CARD_SCALE;
    canvas.height = CARD_H * CARD_SCALE;
    const ctx = canvas.getContext("2d");
    ctx.scale(CARD_SCALE, CARD_SCALE);

    drawCardFrame(ctx, "RE:WATCH MATCH");

    // Poster — the dominant visual. A recognizable piece of pop culture is
    // what makes this worth looking at twice in someone's feed, more than
    // any chart could.
    const posterW = 340;
    const posterH = 510;
    const posterX = (CARD_W - posterW) / 2;
    const posterY = 122;
    ctx.save();
    ctx.beginPath();
    const r = 14;
    ctx.moveTo(posterX + r, posterY);
    ctx.arcTo(posterX + posterW, posterY, posterX + posterW, posterY + posterH, r);
    ctx.arcTo(posterX + posterW, posterY + posterH, posterX, posterY + posterH, r);
    ctx.arcTo(posterX, posterY + posterH, posterX, posterY, r);
    ctx.arcTo(posterX, posterY, posterX + posterW, posterY, r);
    ctx.closePath();
    ctx.clip();
    if (posterImg) {
      ctx.drawImage(posterImg, posterX, posterY, posterW, posterH);
    } else {
      ctx.fillStyle = "#20212a";
      ctx.fillRect(posterX, posterY, posterW, posterH);
    }
    // A subtle bottom gradient so the match badge (drawn next, overlapping
    // the poster's bottom-right corner) stays legible against any artwork.
    const shade = ctx.createLinearGradient(0, posterY + posterH - 100, 0, posterY + posterH);
    shade.addColorStop(0, "rgba(0,0,0,0)");
    shade.addColorStop(1, "rgba(0,0,0,0.55)");
    ctx.fillStyle = shade;
    ctx.fillRect(posterX, posterY + posterH - 100, posterW, 100);
    ctx.restore();

    // Match badge — overlaps the poster's bottom-right corner, same
    // "the number is the payoff" role MatchRing plays in the live UI.
    const badgeCx = posterX + posterW - 6;
    const badgeCy = posterY + posterH - 6;
    const badgeR = 46;
    ctx.beginPath();
    ctx.arc(badgeCx, badgeCy, badgeR, 0, Math.PI * 2);
    ctx.fillStyle = "#14151a";
    ctx.fill();
    ctx.lineWidth = 4;
    ctx.strokeStyle = "#a855f7";
    ctx.stroke();
    ctx.textAlign = "center";
    ctx.fillStyle = "#ffffff";
    ctx.font = "bold 26px 'Plus Jakarta Sans', sans-serif";
    ctx.fillText(`${roundedScore}%`, badgeCx, badgeCy + 4);
    ctx.fillStyle = "#a1a1aa";
    ctx.font = "9px 'Plus Jakarta Sans', sans-serif";
    ctx.fillText("MATCH", badgeCx, badgeCy + 18);

    let y = posterY + posterH + 44;
    ctx.textAlign = "center";
    ctx.fillStyle = "#ffffff";
    ctx.font = "bold 24px 'Plus Jakarta Sans', sans-serif";
    const titleLine = movie.title.length > 30 ? movie.title.slice(0, 30) + "…" : movie.title;
    ctx.fillText(titleLine, CARD_W / 2, y);

    y += 24;
    ctx.fillStyle = "#71717a";
    ctx.font = "13px 'Plus Jakarta Sans', sans-serif";
    ctx.fillText(movie.year || "", CARD_W / 2, y);

    y += 40;
    ctx.strokeStyle = "rgba(255,255,255,0.1)";
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.moveTo(60, y);
    ctx.lineTo(CARD_W - 60, y);
    ctx.stroke();

    y += 32;
    ctx.fillStyle = "#71717a";
    ctx.font = "bold 11px 'Plus Jakarta Sans', sans-serif";
    ctx.fillText(`WHY IT MATCHES ${username.toUpperCase()}`, CARD_W / 2, y);

    y += 30;
    topDrivers.forEach((d) => {
      ctx.fillStyle = "#e4e4e7";
      ctx.font = "15px 'Plus Jakarta Sans', sans-serif";
      ctx.fillText(d, CARD_W / 2, y);
      y += 26;
    });

    drawCardFooter(ctx);

    setexporting(false);
    canvas.toBlob((blob) => {
      shareOrDownloadBlob(blob, `${movie.title.replace(/[^a-z0-9]+/gi, "-")}-match.png`, {
        title: `${roundedScore}% match on Re:Watch`,
        text: `${movie.title} is a ${roundedScore}% match for me on Re:Watch.`
      }).catch(() => setshareErr("Could not share this card."));
    });
  }

  return (
    <div
      className="modal-overlay"
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div
        className="modal-card modal-card--narrow share-card"
        ref={modalRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby="match-share-title"
        tabIndex={-1}
      >
        <div style={{ display: "flex", justifyContent: "center", marginBottom: "8px" }}>
          <BrandMark size={30} />
        </div>
        <span className="eyebrow" style={{ letterSpacing: "0.2em" }}>Re:Watch Match</span>
        <h2 id="match-share-title" style={{ margin: "0 0 12px" }}>{movie.title}</h2>

        <div className="match-share-preview">
          <img
            src={posterUrlFor(movie)}
            alt=""
            aria-hidden="true"
            className="match-share-preview-poster"
            onError={(e) => { e.currentTarget.onerror = null; e.currentTarget.src = FALLBACK_POSTER; }}
          />
          <div className="match-share-preview-badge">{roundedScore}%</div>
        </div>

        {topDrivers.length > 0 && (
          <div className="share-card-traits" style={{ textAlign: "left" }}>
            <p style={{ fontSize: "0.72rem", margin: "0 0 10px", textTransform: "uppercase", color: "var(--text-faint)" }}>
              Why it matches {username}
            </p>
            {topDrivers.map((d) => (
              <div key={d} className="share-card-trait-row" style={{ justifyContent: "flex-start" }}>
                <span>{d}</span>
              </div>
            ))}
          </div>
        )}

        {shareErr && <p className="status-message status-message--error">{shareErr}</p>}

        <div style={{ display: "flex", gap: "10px", marginTop: "var(--sp-2)" }}>
          <button type="button" onClick={onClose} className="btn-block">
            Close
          </button>
          <button type="button" onClick={exportCard} className="btn-primary btn-block" disabled={exporting}>
            {exporting ? "Preparing..." : "Share Match"}
          </button>
        </div>
      </div>
    </div>
  );
}
