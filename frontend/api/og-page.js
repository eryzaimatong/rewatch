// Edge function rewritten to by middleware.js, ONLY for crawler user agents
// hitting /compare/:username or /social/:userId. Returns a minimal static
// HTML document with per-route Open Graph/Twitter meta tags — crawlers don't
// execute JS, so the real SPA's client-rendered <title>/meta never reaches
// them; this is the whole reason the rewrite exists. A real browser never
// reaches this function (see middleware.js's UA gate).
export const config = { runtime: "edge" };

const API_BASE = process.env.VITE_API_BASE || "https://rewatch-backend-edis.onrender.com";

// Render's free tier can take tens of seconds to wake from a cold start.
// Crawlers keep their own short fetch budget (a few seconds, typically) —
// waiting on a cold boot here would make the crawler's request itself time
// out with nothing at all. Failing fast into the generic static fallback
// below is strictly better than either an error or a hung response.
const BACKEND_TIMEOUT_MS = 4500;

function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, (c) => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", "\"": "&quot;", "'": "&#39;"
  }[c]));
}

async function fetchSummary(kind, id) {
  const path = kind === "compare"
    ? `/api/social/username/${encodeURIComponent(id)}/og-summary`
    : `/api/social/${encodeURIComponent(id)}/og-summary`;

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), BACKEND_TIMEOUT_MS);
  try {
    const res = await fetch(`${API_BASE}${path}`, { signal: controller.signal });
    if (!res.ok) {
      // Covers both "genuinely private/nonexistent" (404) and any backend
      // error — either way, the safe move is the same generic fallback,
      // never surfacing which case it was.
      return null;
    }
    return await res.json();
  } catch {
    return null;
  } finally {
    clearTimeout(timeout);
  }
}

const GENERIC_TITLE = "Re:Watch — Stories chosen for how you feel";
const GENERIC_DESCRIPTION = "A recommendation engine built on a real taste model, not popularity.";

export default async function handler(request) {
  const url = new URL(request.url);
  const kind = url.searchParams.get("kind");
  const id = url.searchParams.get("id");
  const origPath = url.searchParams.get("origPath") || "/";
  const origin = url.origin;
  const pageUrl = `${origin}${origPath}`;

  const summary = (kind === "compare" || kind === "social") && id ? await fetchSummary(kind, id) : null;

  let title = GENERIC_TITLE;
  let description = GENERIC_DESCRIPTION;
  let imageUrl = `${origin}/og-image.png`;

  // summary is null for a private profile, a nonexistent one, a cold/failed
  // backend, or a malformed request — all four collapse into the exact same
  // fully-generic preview. No username, no archetype, no counts leak in any
  // of those cases; a crawler and a human both see the identical fallback.
  if (summary) {
    const { username, archetype, topTrait, ratingCount } = summary;
    if (kind === "compare") {
      title = `Test your taste compatibility with ${username} — Re:Watch`;
      description = `${username}'s TasteDNA archetype is ${archetype}. Answer a quick quiz and see how your taste compares — no account needed.`;
    } else {
      title = `${username}'s TasteDNA: ${archetype} — Re:Watch`;
      description = `${ratingCount} rating${ratingCount === 1 ? "" : "s"} logged, leaning ${topTrait ? topTrait.toLowerCase() : "balanced"}. See the full profile on Re:Watch.`;
    }
    const imgParams = new URLSearchParams({ kind, username, archetype });
    if (topTrait) imgParams.set("topTrait", topTrait);
    if (ratingCount != null) imgParams.set("ratingCount", String(ratingCount));
    imageUrl = `${origin}/api/og-image?${imgParams.toString()}`;
  }

  const html = `<!doctype html>
<html lang="en">
<head>
<meta charset="UTF-8" />
<title>${escapeHtml(title)}</title>
<meta property="og:type" content="website" />
<meta property="og:url" content="${escapeHtml(pageUrl)}" />
<meta property="og:site_name" content="Re:Watch" />
<meta property="og:title" content="${escapeHtml(title)}" />
<meta property="og:description" content="${escapeHtml(description)}" />
<meta property="og:image" content="${escapeHtml(imageUrl)}" />
<meta property="og:image:width" content="1200" />
<meta property="og:image:height" content="630" />
<meta name="twitter:card" content="summary_large_image" />
<meta name="twitter:title" content="${escapeHtml(title)}" />
<meta name="twitter:description" content="${escapeHtml(description)}" />
<meta name="twitter:image" content="${escapeHtml(imageUrl)}" />
</head>
<body></body>
</html>`;

  return new Response(html, {
    status: 200,
    headers: {
      "content-type": "text/html; charset=utf-8",
      // Deliberately no caching, at any layer, at any duration — this
      // response's content depends on profilePublic, which a user can flip
      // at any moment. A CDN-cached copy of a since-made-private profile's
      // real username/archetype (confirmed live: a 5-minute cache here let
      // exactly that happen) is the one outcome the privacy requirement
      // above cannot tolerate, so correctness wins over shaving repeat
      // crawler hits to the backend.
      "cache-control": "no-store"
    }
  });
}
