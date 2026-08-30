import { next, rewrite } from "@vercel/edge";

// Every crawler that actually renders a link preview for the platforms this
// app's viral loop depends on. A real visitor's browser UA never matches
// any of these, so this gate only ever affects bots building a preview —
// human traffic always falls through to next() and the normal SPA.
const CRAWLER_UA_PATTERN =
  /facebookexternalhit|Twitterbot|Discordbot|Slackbot|WhatsApp|LinkedInBot|TelegramBot/i;

export const config = {
  matcher: ["/compare/:username", "/social/:userId"]
};

export default function middleware(request) {
  const ua = request.headers.get("user-agent") || "";
  if (!CRAWLER_UA_PATTERN.test(ua)) {
    return next();
  }

  const url = new URL(request.url);
  const [kind, id] = url.pathname.split("/").filter(Boolean);

  const ogPageUrl = new URL("/api/og-page", url.origin);
  ogPageUrl.searchParams.set("kind", kind);
  ogPageUrl.searchParams.set("id", id);
  ogPageUrl.searchParams.set("origPath", url.pathname);

  return rewrite(ogPageUrl);
}
