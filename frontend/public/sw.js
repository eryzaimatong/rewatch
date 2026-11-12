// Hand-written rather than a build plugin (vite-plugin-pwa) — no new
// dependency for what a small stale-while-revalidate cache needs.
//
// API calls are deliberately never cached: a network failure on
// /api/recommendations should surface as a real error, not silently serve
// yesterday's taste profile as if it were live. Static assets (the app
// shell, JS/CSS bundles, images) get stale-while-revalidate — instant from
// cache on repeat visits, refreshed in the background, and available at all
// if the network drops mid-session.
//
// EXCEPTION — the page itself (navigations and "/"): network-first. The HTML is
// what points at the current JS/CSS bundle names, so serving it stale-first
// meant the first visit after every deploy still ran the PREVIOUS release (a
// shipped bug fix appeared to "not work" until a second reload). The cached copy
// is now only a fallback for when the network is genuinely down.
const CACHE_NAME = "rewatch-shell-v3";
const APP_SHELL = ["/", "/manifest.webmanifest", "/icon-192.png", "/icon-512.png"];

self.addEventListener("install", (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => cache.addAll(APP_SHELL)).catch(() => {})
  );
  self.skipWaiting();
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter((k) => k !== CACHE_NAME).map((k) => caches.delete(k)))
    )
  );
  self.clients.claim();
});

self.addEventListener("fetch", (event) => {
  const { request } = event;
  if (request.method !== "GET") {
    return;
  }

  const url = new URL(request.url);
  if (url.pathname.startsWith("/api/")) {
    return;
  }

  const isPage = request.mode === "navigate" || url.pathname === "/";
  if (isPage) {
    event.respondWith(
      fetch(request)
        .then((response) => {
          if (response.ok) {
            const clone = response.clone();
            caches.open(CACHE_NAME).then((cache) => cache.put("/", clone));
          }
          return response;
        })
        .catch(() => caches.match("/").then((cached) => cached || Response.error()))
    );
    return;
  }

  event.respondWith(
    caches.match(request).then((cached) => {
      const networkFetch = fetch(request)
        .then((response) => {
          if (response.ok) {
            const clone = response.clone();
            caches.open(CACHE_NAME).then((cache) => cache.put(request, clone));
          }
          return response;
        })
        .catch(() => cached);
      return cached || networkFetch;
    })
  );
});
