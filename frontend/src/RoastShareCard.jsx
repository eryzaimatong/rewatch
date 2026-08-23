import { useEffect, useState } from "react";
import useModalA11y from "./useModalA11y";
import BrandMark from "./BrandMark";
import { authHeaders } from "./auth";
import { BASE } from "./api";
import {
  CARD_SCALE, CARD_W, CARD_H, drawCardFrame, drawCardFooter, shareOrDownloadBlob, wrapText
} from "./shareCard";
import "./App.css";

function shareLinkFor() {
  return window.location.origin;
}

/**
 * "Roast My Taste" — the sharpest, funniest thing this app can say about you,
 * built to be screenshotted and posted the way a savage horoscope or a
 * Spotify Wrapped hot take gets posted. Every line comes straight from
 * RoastService — real numbers computed from this user's own ratings, never
 * generic filler — so it reads as personal instead of a template with your
 * name swapped in. Same canvas-export plumbing as MatchShareCard/Wrapped
 * (see shareCard.js); the difference is the content is built to be funny and
 * a little mean, not celebratory.
 */
export default function RoastShareCard({ userId, username, onClose }) {
  const modalRef = useModalA11y(onClose);
  const [roast, setroast] = useState(null);
  const [loading, setloading] = useState(true);
  const [notEnoughData, setnotEnoughData] = useState(false);
  const [exporting, setexporting] = useState(false);
  const [shareErr, setshareErr] = useState("");
  const [linkCopied, setlinkCopied] = useState(false);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const res = await fetch(`${BASE}/api/tastedna/roast/${userId}`, { headers: authHeaders() }).catch(() => null);
      if (cancelled) return;
      if (res && res.status === 404) {
        setnotEnoughData(true);
      } else if (res && res.ok) {
        setroast(await res.json());
      } else {
        setshareErr("Could not load your roast right now.");
      }
      setloading(false);
    })();
    return () => { cancelled = true; };
  }, [userId]);

  async function copyLink() {
    try {
      await navigator.clipboard.writeText(shareLinkFor());
      setlinkCopied(true);
      setTimeout(() => setlinkCopied(false), 2000);
    } catch {
      setshareErr("Could not copy the link.");
    }
  }

  async function exportCard() {
    if (!roast) return;
    setexporting(true);
    setshareErr("");

    const canvas = document.createElement("canvas");
    canvas.width = CARD_W * CARD_SCALE;
    canvas.height = CARD_H * CARD_SCALE;
    const ctx = canvas.getContext("2d");
    ctx.scale(CARD_SCALE, CARD_SCALE);

    drawCardFrame(ctx, "TASTE ROAST");

    ctx.textAlign = "center";
    ctx.fillStyle = "#71717a";
    ctx.font = "13px 'Plus Jakarta Sans', sans-serif";
    ctx.fillText(`@${username}`, CARD_W / 2, 140);

    ctx.fillStyle = "#ffffff";
    ctx.font = "bold 30px 'Plus Jakarta Sans', sans-serif";
    let y = wrapText(ctx, roast.headline, CARD_W / 2, 200, CARD_W - 100, 38);

    y += 30;
    ctx.strokeStyle = "rgba(255,255,255,0.1)";
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.moveTo(60, y);
    ctx.lineTo(CARD_W - 60, y);
    ctx.stroke();

    y += 34;
    ctx.fillStyle = "#a855f7";
    ctx.font = "bold 11px 'Plus Jakarta Sans', sans-serif";
    ctx.fillText("THE RECEIPTS", CARD_W / 2, y);

    y += 34;
    ctx.fillStyle = "#e4e4e7";
    ctx.font = "16px 'Plus Jakarta Sans', sans-serif";
    roast.receipts.forEach((line) => {
      y = wrapText(ctx, line, CARD_W / 2, y, CARD_W - 120, 24);
      y += 26;
    });

    drawCardFooter(ctx);

    setexporting(false);
    canvas.toBlob((blob) => {
      shareOrDownloadBlob(blob, `${username}-taste-roast.png`, {
        title: "I got roasted by Re:Watch",
        text: roast.headline
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
        aria-labelledby="roast-share-title"
        tabIndex={-1}
      >
        <div style={{ display: "flex", justifyContent: "center", marginBottom: "8px" }}>
          <BrandMark size={30} />
        </div>
        <span className="eyebrow" style={{ letterSpacing: "0.2em" }}>Taste Roast</span>
        <h2 id="roast-share-title" style={{ margin: "0 0 12px" }}>@{username}</h2>

        {loading && <p style={{ color: "var(--text-muted)" }}>Reading your receipts...</p>}

        {!loading && notEnoughData && (
          <p style={{ color: "var(--text-muted)" }}>
            Rate a few more titles before we can roast you properly — we need real receipts, not vibes.
          </p>
        )}

        {!loading && roast && (
          <>
            <p style={{ fontSize: "1.15rem", fontWeight: 700, margin: "0 0 var(--sp-2)" }}>
              {roast.headline}
            </p>
            <div className="share-card-traits" style={{ textAlign: "left" }}>
              <p style={{ fontSize: "0.72rem", margin: "0 0 10px", textTransform: "uppercase", color: "var(--text-faint)" }}>
                The receipts
              </p>
              {roast.receipts.map((line) => (
                <div key={line} className="share-card-trait-row" style={{ justifyContent: "flex-start" }}>
                  <span>{line}</span>
                </div>
              ))}
            </div>
          </>
        )}

        {shareErr && <p className="status-message status-message--error">{shareErr}</p>}

        {roast && (
          <div style={{ display: "flex", gap: "10px", marginTop: "var(--sp-2)" }}>
            <button type="button" onClick={copyLink} className="btn-block">
              {linkCopied ? "Link copied" : "Copy link"}
            </button>
            <button type="button" onClick={exportCard} className="btn-primary btn-block" disabled={exporting}>
              {exporting ? "Preparing..." : "Download image"}
            </button>
          </div>
        )}
        <button type="button" onClick={onClose} className="btn-block" style={{ marginTop: "8px" }}>
          Close
        </button>
      </div>
    </div>
  );
}
