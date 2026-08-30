// Every crawler that actually renders a link preview for the platforms this
// app's viral loop depends on. A real visitor's browser UA never matches
// any of these, so this gate only ever affects bots building a preview —
// human traffic always falls through unchanged to the normal SPA.
const CRAWLER_UA_PATTERN =
  /facebookexternalhit|Twitterbot|Discordbot|Slackbot|WhatsApp|LinkedInBot|TelegramBot/i;

export const config = {
  matcher: ["/compare/:username", "/social/:userId"]
};

/**
 * Deliberately no @vercel/edge import — confirmed live via `vercel inspect
 * --logs` that it broke the ENTIRE deployment ("Edge Function 'middleware'
 * is referencing unsupported modules: @vercel"), not just this function,
 * which silently kept the whole site on a stale build for several pushes
 * (see DEPLOYMENT.md). next()/rewrite() are trivial to hand-roll as plain
 * Response objects carrying the same x-middleware-* headers Vercel's edge
 * runtime already looks for, so nothing is lost by not depending on it.
 */
export default function middleware(request) {
  const ua = request.headers.get("user-agent") || "";
  if (!CRAWLER_UA_PATTERN.test(ua)) {
    return; // undefined = continue to normal SPA routing, untouched
  }

  const url = new URL(request.url);
  const [kind, id] = url.pathname.split("/").filter(Boolean);

  const ogPageUrl = new URL("/api/og-page", url.origin);
  ogPageUrl.searchParams.set("kind", kind);
  ogPageUrl.searchParams.set("id", id);
  ogPageUrl.searchParams.set("origPath", url.pathname);

  return new Response(null, {
    headers: { "x-middleware-rewrite": ogPageUrl.toString() }
  });
}
