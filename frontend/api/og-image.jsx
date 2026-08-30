import { ImageResponse } from "@vercel/og";

// Edge runtime — same environment class as middleware.js, needed for
// ImageResponse (satori) to run at request time close to the crawler.
export const config = { runtime: "edge" };

// Ported from shareCard.js's drawCardFrame/drawCardFooter — same gradient,
// same purple accent, same footer tagline and wordmark. This is the
// landscape (1200x630, link-preview) cousin of shareCard.js's 540x960
// Stories-shaped cards; deliberately not a new visual system.
const PURPLE = "#a855f7";
const BG_TOP = "#1f2029";
const BG_BOTTOM = "#14151a";
const FONT_FAMILY = "Plus Jakarta Sans";

/**
 * Google Fonts serves a plain .ttf (not .woff2) when the request has no
 * "sophisticated" browser User-Agent — satori (the renderer behind
 * ImageResponse) can only consume ttf/otf/woff, not woff2, so the fetch
 * below deliberately sends no UA override and relies on that default.
 * `text` narrows the returned subset to only the glyphs actually needed.
 */
async function loadFont(text) {
  try {
    const cssUrl = `https://fonts.googleapis.com/css2?family=${encodeURIComponent(FONT_FAMILY)}:wght@700&text=${encodeURIComponent(text)}`;
    const css = await (await fetch(cssUrl)).text();
    const match = css.match(/src: url\(([^)]+)\) format\('(opentype|truetype)'\)/);
    if (match) {
      const res = await fetch(match[1]);
      if (res.ok) {
        return await res.arrayBuffer();
      }
    }
  } catch {
    // Falls through to satori's built-in sans-serif below — a font-load
    // hiccup should never be the reason a share image fails to render.
  }
  return null;
}

export default async function handler(request) {
  const { searchParams } = new URL(request.url);
  const kind = searchParams.get("kind");
  const username = searchParams.get("username");
  const archetype = searchParams.get("archetype");
  const topTrait = searchParams.get("topTrait");
  const ratingCount = searchParams.get("ratingCount");

  const eyebrow = kind === "compare" ? "TASTE COMPATIBILITY" : kind === "social" ? "TASTEDNA PROFILE" : "RE:WATCH";

  const heading = username && archetype
    ? (kind === "compare" ? `${username}'s taste: ${archetype}` : `${username}: ${archetype}`)
    : "Stories chosen for how you feel.";

  const sub = username
    ? [topTrait ? `Leans ${topTrait.toLowerCase()}` : null, ratingCount ? `${ratingCount} ratings` : null]
        .filter(Boolean)
        .join(" · ")
    : "A recommendation engine built on a real taste model, not popularity.";

  const fontData = await loadFont(`${eyebrow}${heading}${sub}Stories chosen for how you feel.RE:WATCH`);

  return new ImageResponse(
    (
      <div
        style={{
          width: "1200px",
          height: "630px",
          display: "flex",
          flexDirection: "column",
          alignItems: "center",
          justifyContent: "space-between",
          background: `linear-gradient(135deg, ${BG_TOP} 0%, ${BG_BOTTOM} 100%)`,
          border: "2px solid rgba(168,85,247,0.5)",
          padding: "64px",
          fontFamily: fontData ? FONT_FAMILY : "sans-serif"
        }}
      >
        <div style={{ display: "flex", alignItems: "center", gap: "14px" }}>
          <div style={{ width: "30px", height: "30px", borderRadius: "50%", border: `2px solid ${PURPLE}`, opacity: 0.85 }} />
          <span style={{ color: PURPLE, fontSize: "20px", fontWeight: 700, letterSpacing: "3px" }}>{eyebrow}</span>
        </div>

        <div style={{ display: "flex", flexDirection: "column", alignItems: "center", textAlign: "center", gap: "22px", maxWidth: "920px" }}>
          <span style={{ color: "#f4f4f5", fontSize: "56px", fontWeight: 700, lineHeight: 1.2 }}>{heading}</span>
          {sub && <span style={{ color: "#a1a1aa", fontSize: "26px" }}>{sub}</span>}
        </div>

        <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: "10px" }}>
          <div style={{ width: "48px", height: "1px", background: "rgba(255,255,255,0.16)" }} />
          <span style={{ color: "#71717a", fontSize: "18px" }}>Stories chosen for how you feel.</span>
          <span style={{ color: PURPLE, fontSize: "20px", fontWeight: 700, letterSpacing: "1px" }}>RE:WATCH</span>
        </div>
      </div>
    ),
    {
      width: 1200,
      height: 630,
      fonts: fontData ? [{ name: FONT_FAMILY, data: fontData, weight: 700, style: "normal" }] : undefined
    }
  );
}
