import { useEffect, useState } from "react";
import TraitRadar from "./TraitRadar";
import EvolutionTimeline from "./EvolutionTimeline";
import BrandMark from "./BrandMark";
import RoastShareCard from "./RoastShareCard";
import useModalA11y from "./useModalA11y";
import { authHeaders } from "./auth";
import { BASE } from "./api";
import {
  CARD_SCALE, CARD_W, CARD_H, drawCardFrame, drawCardFooter, svgElementToImage, shareOrDownloadBlob, wrapText
} from "./shareCard";
import "./App.css";

function getpercent(node) {
  if (!node) {
    return 50;
  }
  return Math.round(node.val * 100);
}

// A separate component (rather than inline JSX gated by `showcard &&`) so
// useModalA11y's mount/unmount lifecycle — the focus trap, the body scroll
// lock — actually matches the modal's real open/close lifecycle, instead of
// running for as long as the whole TasteProfile page is mounted.
function ShareCardModal({
  username, archetype, archetypeBlurb, topthree,
  radarTraits, radarValues, radarConfidences,
  exporting, onExport, onClose
}) {
  const modalRef = useModalA11y(onClose);

  return (
    <div
      className="modal-overlay"
      onClick={(e) => {
        if (e.target === e.currentTarget) {
          onClose();
        }
      }}
    >
      <div
        className="modal-card modal-card--narrow share-card"
        ref={modalRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby="share-card-title"
        tabIndex={-1}
      >
        <div style={{ display: "flex", justifyContent: "center", marginBottom: "8px" }}>
          <BrandMark size={30} />
        </div>
        <span className="eyebrow" style={{ letterSpacing: "0.2em" }}>Re:Watch TasteDNA</span>
        <h2 id="share-card-title" style={{ margin: "0 0 6px" }}>{username}'s Card</h2>
        <span className="share-card-badge">{archetype}</span>
        <p className="brand-tagline" style={{ marginBottom: "var(--sp-2)" }}>
          Stories chosen for how you feel.
        </p>

        <TraitRadar
          traits={radarTraits}
          primary={{ label: "Your TasteDNA", color: "#a855f7", values: radarValues, confidences: radarConfidences }}
          size={220}
        />

        <div className="share-card-traits">
          <p style={{ fontSize: "0.72rem", margin: "0 0 10px", textTransform: "uppercase", color: "var(--text-faint)" }}>
            Top Storytelling Traits
          </p>
          {topthree.map((item, i) => (
            <div key={item.key} className="share-card-trait-row">
              <span>#{i + 1} {item.name}</span>
              <span style={{ color: "var(--primary-light)", fontWeight: "700" }}>{getpercent(item.node)}%</span>
            </div>
          ))}
        </div>

        <p style={{ fontSize: "0.85rem", lineHeight: "1.6", marginBottom: "var(--sp-3)", textAlign: "left" }}>
          "{archetypeBlurb}"
        </p>

        <div style={{ display: "flex", gap: "10px" }}>
          <button type="button" onClick={onClose} className="btn-block">
            Close
          </button>
          <button type="button" onClick={onExport} className="btn-primary btn-block" disabled={exporting}>
            {exporting ? "Exporting..." : "Share Card"}
          </button>
        </div>
      </div>
    </div>
  );
}

// Fallback labels for the offline backup profile below, where the API's own
// `label` field (the source of truth once loaded) isn't available yet.
const TRAIT_ORDER = [
  "nostalgia", "family", "growth", "comfort", "pacing",
  "hope", "bitter", "humor", "romance", "intensity"
];
// The 6 axes that carry the most signal for "what kind of stories do you
// respond to" at a glance — full 10-axis detail (nostalgia/family/humor/
// romance included) stays one click away via the Advanced toggle rather
// than crowding the default view.
const MACRO_TRAIT_KEYS = ["pacing", "intensity", "comfort", "bitter", "growth", "hope"];
const FALLBACK_LABELS = {
  nostalgia: "Nostalgia & Memory",
  family: "Found Family",
  growth: "Character Growth",
  comfort: "Comfort & Warmth",
  pacing: "Slow Burn Pacing",
  hope: "Hopeful Payoff",
  bitter: "Bittersweet Drama",
  humor: "Humor & Lightheartedness",
  romance: "Romance",
  intensity: "Emotional Intensity"
};

export default function TasteProfile() {
  const [traits, settraits] = useState({});
  const [archetype, setarchetype] = useState("Emotional Storyteller");
  const [archetypeBlurb, setarchetypeBlurb] = useState("You enjoy balanced storytelling across multiple emotional tones.");
  const [meanConfidence, setmeanConfidence] = useState(0.1);
  const [ratingCount, setratingCount] = useState(0);
  const [personalized, setpersonalized] = useState(false);
  const [loading, setloading] = useState(true);
  const [msg, setmsg] = useState("");
  const [err, seterr] = useState("");
  const [showcard, setshowcard] = useState(false);
  const [showroast, setshowroast] = useState(false);
  const [recalculating, setrecalculating] = useState(false);
  const [exporting, setexporting] = useState(false);
  const [advancedRadar, setadvancedRadar] = useState(false);

  const username = localStorage.getItem("username") || "friend";
  const userid = localStorage.getItem("userId") || 1;

  useEffect(() => {
    loadprofile();
  }, []);

  async function loadprofile() {
    setloading(true);
    seterr("");

    const res = await fetch(`${BASE}/api/tastedna/profile/` + userid, { headers: authHeaders() }).catch(() => null);

    if (res && res.ok) {
      const data = await res.json();
      settraits(data.traits ?? {});
      setarchetype(data.archetype || "Emotional Storyteller");
      setarchetypeBlurb(data.archetypeBlurb || "");
      setmeanConfidence(typeof data.meanConfidence === "number" ? data.meanConfidence : 0.1);
      setratingCount(typeof data.ratingCount === "number" ? data.ratingCount : 0);
      setpersonalized(!!data.personalized);
      setloading(false);
      return;
    }

    seterr("Could not reach the server — showing a sample profile instead.");
    const backup = {
      comfort: { val: 0.5, conf: 0.1, trend: 0, evid: 0, label: FALLBACK_LABELS.comfort },
      family: { val: 0.5, conf: 0.1, trend: 0, evid: 0, label: FALLBACK_LABELS.family },
      nostalgia: { val: 0.5, conf: 0.1, trend: 0, evid: 0, label: FALLBACK_LABELS.nostalgia },
      hope: { val: 0.5, conf: 0.1, trend: 0, evid: 0, label: FALLBACK_LABELS.hope },
      romance: { val: 0.5, conf: 0.1, trend: 0, evid: 0, label: FALLBACK_LABELS.romance },
      bitter: { val: 0.5, conf: 0.1, trend: 0, evid: 0, label: FALLBACK_LABELS.bitter },
      growth: { val: 0.5, conf: 0.1, trend: 0, evid: 0, label: FALLBACK_LABELS.growth },
      pacing: { val: 0.5, conf: 0.1, trend: 0, evid: 0, label: FALLBACK_LABELS.pacing },
      humor: { val: 0.5, conf: 0.1, trend: 0, evid: 0, label: FALLBACK_LABELS.humor },
      intensity: { val: 0.5, conf: 0.1, trend: 0, evid: 0, label: FALLBACK_LABELS.intensity }
    };
    settraits(backup);
    setloading(false);
  }

  function gettrendtext(node) {
    if (!node || node.trend === 0) {
      return "→ Stable";
    }
    return node.trend > 0 ? "↑ Rising" : "↓ Falling";
  }

  const list = TRAIT_ORDER.map((key) => ({
    key,
    name: traits[key]?.label || FALLBACK_LABELS[key],
    node: traits[key]
  }));

  const sorted = [...list].sort((a, b) => getpercent(b.node) - getpercent(a.node));
  const topthree = sorted.slice(0, 3);

  const radarKeys = advancedRadar ? TRAIT_ORDER : MACRO_TRAIT_KEYS;
  const radarTraits = radarKeys.map((key) => ({ key, label: traits[key]?.label || FALLBACK_LABELS[key] }));
  const radarValues = {};
  const radarConfidences = {};
  radarKeys.forEach((key) => {
    radarValues[key] = traits[key]?.val ?? 0.5;
    radarConfidences[key] = traits[key]?.conf ?? 0.1;
  });

  async function syncall() {
    setmsg("");
    seterr("");
    setrecalculating(true);

    const res = await fetch(`${BASE}/api/tastedna/replay/` + userid, {
      method: "POST",
      headers: authHeaders()
    }).catch(() => null);

    setrecalculating(false);

    if (!res || !res.ok) {
      seterr("Could not recompute your profile right now.");
      return;
    }

    setmsg("Done — your TasteDNA has been rebuilt from your complete rating history.");
    loadprofile();
  }

  async function exportTasteCardPNG() {
    const svg = document.getElementById("profile-radar-svg");
    if (!svg) {
      return;
    }
    setexporting(true);

    // A serialized SVG loaded standalone (via a blob: URL into an <img>) has no
    // access to this page's external stylesheet — only inline styles and any
    // <style> embedded IN the SVG itself survive. The radar's series colors are
    // already inline (see TraitRadar.jsx), but the grid/spokes/axis-label/dot
    // classes live in App.css and would render unstyled (or invisible) without
    // this. Literal resolved values, not CSS custom properties — var(--x)
    // also wouldn't resolve in a detached document.
    let img;
    try {
      img = await svgElementToImage(svg, `
        .radar-grid-ring { stroke: rgba(255,255,255,0.15); stroke-width: 1; }
        .radar-spoke { stroke: rgba(255,255,255,0.085); stroke-width: 1; }
        .radar-confidence-band { stroke: none; opacity: 0.16; }
        .radar-series-path { stroke-width: 2; fill-opacity: 0.1; }
        .radar-dot { stroke: #1f2029; stroke-width: 2; paint-order: stroke fill; }
        .radar-axis-label { fill: #71717a; font-size: 0.62rem; font-weight: 600; font-family: 'Plus Jakarta Sans', sans-serif; }
      `);
    } catch {
      setexporting(false);
      return;
    }

    const imgSize = 340;
    const canvas = document.createElement("canvas");
    canvas.width = CARD_W * CARD_SCALE;
    canvas.height = CARD_H * CARD_SCALE;
    const ctx = canvas.getContext("2d");
    ctx.scale(CARD_SCALE, CARD_SCALE);

    drawCardFrame(ctx, "RE:WATCH TASTEDNA");

    ctx.textAlign = "center";
    ctx.fillStyle = "#ffffff";
    ctx.font = "bold 30px 'Plus Jakarta Sans', sans-serif";
    ctx.fillText(`${username}'s Card`, CARD_W / 2, 144);

    ctx.fillStyle = "#d8b4fe";
    ctx.font = "bold 18px 'Plus Jakarta Sans', sans-serif";
    ctx.fillText(archetype, CARD_W / 2, 174);

    const imgTop = 200;
    ctx.drawImage(img, (CARD_W - imgSize) / 2, imgTop, imgSize, imgSize);

    ctx.fillStyle = "#a1a1aa";
    ctx.font = "13px 'Plus Jakarta Sans', sans-serif";
    ctx.textAlign = "left";
    wrapText(ctx, archetypeBlurb, 36, imgTop + imgSize + 36, CARD_W - 72, 19);

    drawCardFooter(ctx);

    setexporting(false);
    canvas.toBlob((blob) => {
      shareOrDownloadBlob(blob, `${username}-tastedna-card.png`, {
        title: "My Re:Watch TasteDNA",
        text: `My TasteDNA archetype on Re:Watch: ${archetype}`
      });
    });
  }

  return (
    <div className="page-shell">
      <div className="page-panel taste-panel">
        <div className="taste-panel-head">
          <div>
            <span className="eyebrow">Evolving TasteDNA</span>
            <h1>{username}'s Storytelling Vector</h1>
          </div>

          <div style={{ display: "flex", gap: "10px" }}>
            <button type="button" onClick={() => setshowroast(true)} className="btn-block">
              Roast My Taste
            </button>
            <button type="button" onClick={() => setshowcard(true)} className="btn-primary">
              Export Taste Card
            </button>
          </div>
        </div>

        <p className="taste-panel-intro">
          {personalized
            ? "Your TasteDNA evolves from every rating you submit."
            : "This is your starting profile — rate a few titles and it will start to move."}
        </p>

        <div className="archetype-banner">
          <div className="archetype-banner-row">
            <span className="archetype-label">Active Archetype: {archetype}</span>
            {/* Below 40% (this app's own "Signal Forming" achievement threshold —
                see AchievementService.CONFIDENCE_TIERS), a raw percentage reads as
                a broken number ("Confidence 11%") rather than useful context. A
                real count of what's actually been logged says the same true thing
                without needing the reader to know what "confidence" measures. */}
            {meanConfidence < 0.4 ? (
              <span className="archetype-confidence">
                Still forming — {ratingCount} rated so far
              </span>
            ) : (
              <span className="archetype-confidence">{Math.round(meanConfidence * 100)}% TasteDNA confidence</span>
            )}
          </div>
          <p className="archetype-blurb">{archetypeBlurb}</p>
        </div>

        {loading && (
          <div className="feed-state">
            <div className="loading-orb" />
            <p>Loading your trait nodes...</p>
          </div>
        )}

        {!loading && (
          <>
            <TraitRadar
              svgId="profile-radar-svg"
              traits={radarTraits}
              primary={{ label: "Your TasteDNA", color: "#a855f7", values: radarValues, confidences: radarConfidences }}
              size={360}
              className="taste-panel-radar"
            />
            <button
              type="button"
              className="pill radar-detail-toggle"
              onClick={() => setadvancedRadar((v) => !v)}
            >
              {advancedRadar ? "Show 6-axis summary" : "Show all 10 axes"}
            </button>

            <EvolutionTimeline
              userId={userid}
              allTraits={TRAIT_ORDER.map((key) => ({ key, label: traits[key]?.label || FALLBACK_LABELS[key] }))}
              defaultKeys={topthree.map((t) => t.key)}
            />

            <div className="trait-list" style={{ marginTop: "var(--sp-3)" }}>
              {list.map((item) => {
                const p = getpercent(item.node);
                const conf = item.node ? Math.round(item.node.conf * 100) : 10;
                const evid = item.node ? item.node.evid : 0;
                const trendstr = gettrendtext(item.node);

                return (
                  <div key={item.key} className="trait-card">
                    <div className="trait-card-row">
                      <div>
                        <span className="trait-name">{item.name}</span>
                        <span className="trait-trend">{trendstr}</span>
                      </div>
                      <span className="trait-percent">{p}%</span>
                    </div>

                    <div className="trait-bar-track">
                      <div className="trait-bar-fill" style={{ width: `${p}%` }}></div>
                    </div>

                    <div className="trait-meta-row">
                      <span>Confidence: {conf}%</span>
                      <span>{evid === 0 ? "No ratings yet" : `From ${evid} rating${evid === 1 ? "" : "s"}`}</span>
                    </div>
                  </div>
                );
              })}
            </div>
          </>
        )}

        {err && <p className="status-message status-message--error">{err}</p>}
        {msg && <p className="status-message status-message--success">{msg}</p>}

        <div style={{ display: "flex", justifyContent: "flex-end" }}>
          <button type="button" onClick={syncall} className="btn-primary" disabled={recalculating}>
            {recalculating ? "Rebuilding..." : "Rebuild My TasteDNA"}
          </button>
        </div>
      </div>

      {showcard && (
        <ShareCardModal
          username={username}
          archetype={archetype}
          archetypeBlurb={archetypeBlurb}
          topthree={topthree}
          radarTraits={radarTraits}
          radarValues={radarValues}
          radarConfidences={radarConfidences}
          exporting={exporting}
          onExport={exportTasteCardPNG}
          onClose={() => setshowcard(false)}
        />
      )}

      {showroast && (
        <RoastShareCard
          userId={userid}
          username={username}
          onClose={() => setshowroast(false)}
        />
      )}
    </div>
  );
}
