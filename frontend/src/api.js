import { authHeaders } from "./auth";

export const BASE = import.meta.env.VITE_API_BASE ?? "http://localhost:8080";

// Every exported function below returns exactly the same value it always
// has on every path (same array/object/null/number, same fallback) — no
// caller needs to change. In addition, when that value is a non-null
// object or array, a hidden API_META tag is attached distinguishing three
// outcomes a caller currently cannot tell apart by looking at an empty
// array or a null: the request succeeded, it reached the server and got
// back an HTTP error, or it never reached the server at all (offline, DNS,
// an aborted request — fetch() itself rejects in that case, and an
// uncaught rejection here used to propagate straight out of callers like
// MovieFeed's loadData(), skipping every statement after the await
// including setLoading(false) and leaving the feed on its skeleton loader
// forever). API_META is a Symbol key: it's invisible to Object.keys,
// JSON.stringify, spread, and every existing caller — read it explicitly
// via getApiMeta(result) when a caller is ready to render ErrorState
// instead of treating "failed to load" the same as "loaded, empty".
export const API_META = Symbol("apiMeta");

export function getApiMeta(result) {
  return result != null && typeof result === "object" ? result[API_META] : undefined;
}

function withMeta(value, meta) {
  if (value !== null && typeof value === "object") {
    try {
      Object.defineProperty(value, API_META, { value: meta, enumerable: false, configurable: true });
    } catch {
      // Frozen/sealed response body — extremely unlikely for a fetch()
      // result, but meta is a bonus channel, never a requirement.
    }
  }
  return value;
}

// Guards the fetch() call itself, not the response. A resolved fetch (even
// a 4xx/5xx) is an HTTP-layer outcome; a rejected fetch is network-layer —
// the caller couldn't reach the server at all. Collapsing both into the
// same catch is exactly the bug this file used to have everywhere except
// gettitles()/getrecs().
async function safeFetch(url, options) {
  try {
    const res = await fetch(url, options);
    return { res, meta: { ok: res.ok, status: res.status, type: res.ok ? undefined : "http" } };
  } catch (e) {
    return { res: null, meta: { ok: false, type: "network", message: e.message } };
  }
}

export async function gettitles() {
  const { res, meta } = await safeFetch(`${BASE}/api/titles`);
  if (!res) {
    return withMeta([], meta);
  }
  if (!res.ok) {
    return withMeta([], meta);
  }
  return withMeta(await res.json().catch(() => []), meta);
}

// Onboarding's favourites picker specifically — a paginated, projected
// (id/title/poster/type/popularity only) alternative to gettitles(), which
// ships the full ~6,000-row catalog with every field just to render 60
// posters. `selected` keeps already-picked titles visible even if a new
// search/tab wouldn't otherwise surface them (server enforces this, see
// TitleController.picker).
export async function gettitlepicker(bucket, search, selected) {
  const params = new URLSearchParams({ bucket });
  if (search) {
    params.set("search", search);
  }
  (selected ?? []).forEach((title) => params.append("selected", title));
  const { res, meta } = await safeFetch(`${BASE}/api/titles/picker?${params.toString()}`, {
    headers: authHeaders()
  });
  if (!res || !res.ok) {
    return withMeta([], meta);
  }
  return withMeta(await res.json().catch(() => []), meta);
}

// Additive "Sharpen your TasteDNA" refinement, offered from the dashboard
// after onboarding's first result — merges into the existing seed rather
// than overwriting it (see OnboardingService.refinementRawDelta), so this
// deliberately does not resend the original 5 favourites.
export async function refineOnboarding(payload) {
  const { res, meta } = await safeFetch(`${BASE}/api/movies/onboard/refine`, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...authHeaders() },
    body: JSON.stringify(payload)
  });
  if (!res) {
    return withMeta({ ok: false, data: null }, meta);
  }
  return withMeta({ ok: res.ok, data: await res.json().catch(() => null) }, meta);
}

export async function rateMovie(payload) {
  const { res, meta } = await safeFetch(`${BASE}/api/movies/rate`, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...authHeaders() },
    body: JSON.stringify(payload)
  });
  if (!res) {
    return withMeta({ ok: false, data: null }, meta);
  }
  return withMeta({ ok: res.ok, data: await res.json().catch(() => null) }, meta);
}

export async function deleteRating(ratingId, userId) {
  const { res, meta } = await safeFetch(`${BASE}/api/movies/ratings/${ratingId}?userId=${userId}`, {
    method: "DELETE",
    headers: authHeaders()
  });
  if (!res) {
    return withMeta({ ok: false, data: null }, meta);
  }
  return withMeta({ ok: res.ok, data: await res.json().catch(() => null) }, meta);
}

export async function getWatchStatuses(userId) {
  const { res, meta } = await safeFetch(`${BASE}/api/watch-status/${userId}`, { headers: authHeaders() });
  if (!res || !res.ok) {
    return withMeta({}, meta);
  }
  return withMeta(await res.json().catch(() => ({})), meta);
}

export async function setWatchStatus(userId, titleId, status) {
  const { res, meta } = await safeFetch(`${BASE}/api/watch-status`, {
    method: "PUT",
    headers: { "Content-Type": "application/json", ...authHeaders() },
    body: JSON.stringify({ userId, titleId, status })
  });
  if (!res) {
    return withMeta(null, meta);
  }
  return withMeta(await res.json().catch(() => null), meta);
}

export async function getrecs(id) {
  // See gettitles() above — same network-vs-HTTP-error gap, same fix.
  const { res, meta } = await safeFetch(`${BASE}/api/recommendations/${id}`, { headers: authHeaders() });
  if (!res || !res.ok) {
    return withMeta([], meta);
  }
  return withMeta(await res.json().catch(() => []), meta);
}

export async function login(user, pass) {
  const { res, meta } = await safeFetch(`${BASE}/api/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username: user, password: pass })
  });
  if (!res) {
    return withMeta(null, meta);
  }
  // Parsed either way (not just on !res.ok) — the backend's error responses
  // carry a real `message` (e.g. "Invalid username/email or password") that
  // callers want to show, not just a generic failure.
  return withMeta(await res.json().catch(() => null), meta);
}

export async function register(user, pass, email) {
  // `email` is NOT NULL on the User entity, so omitting it made register 500.
  const { res, meta } = await safeFetch(`${BASE}/api/auth/register`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      username: user,
      password: pass,
      email: email && email.trim() ? email.trim() : `${user}@rewatch.local`
    })
  });
  if (!res) {
    return withMeta(null, meta);
  }
  return withMeta(await res.json().catch(() => null), meta);
}

export async function forgotPassword(email) {
  const { res, meta } = await safeFetch(`${BASE}/api/auth/forgot-password`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email })
  });
  if (!res) {
    return withMeta(null, meta);
  }
  return withMeta(await res.json().catch(() => null), meta);
}

export async function resetPassword(token, newPassword) {
  const { res, meta } = await safeFetch(`${BASE}/api/auth/reset-password`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ token, newPassword })
  });
  if (!res) {
    return withMeta(null, meta);
  }
  return withMeta(await res.json().catch(() => null), meta);
}

export async function changePassword(userId, currentPassword, newPassword) {
  const { res, meta } = await safeFetch(`${BASE}/api/account/change-password`, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...authHeaders() },
    body: JSON.stringify({ userId, currentPassword, newPassword })
  });
  if (!res) {
    return withMeta(null, meta);
  }
  return withMeta(await res.json().catch(() => null), meta);
}

export async function getEmail(userId) {
  const { res, meta } = await safeFetch(`${BASE}/api/account/email/${userId}`, { headers: authHeaders() });
  if (!res) {
    return withMeta(null, meta);
  }
  return withMeta(await res.json().catch(() => null), meta);
}

export async function changeEmail(userId, currentPassword, newEmail) {
  const { res, meta } = await safeFetch(`${BASE}/api/account/email`, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...authHeaders() },
    body: JSON.stringify({ userId, currentPassword, newEmail })
  });
  if (!res) {
    return withMeta(null, meta);
  }
  return withMeta(await res.json().catch(() => null), meta);
}

export async function getProfileVisibility(userId) {
  const { res, meta } = await safeFetch(`${BASE}/api/account/profile-visibility/${userId}`, { headers: authHeaders() });
  if (!res) {
    return withMeta(null, meta);
  }
  return withMeta(await res.json().catch(() => null), meta);
}

export async function setProfileVisibility(userId, isPublic) {
  const { res, meta } = await safeFetch(`${BASE}/api/account/profile-visibility`, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...authHeaders() },
    body: JSON.stringify({ userId, isPublic })
  });
  if (!res) {
    return withMeta(null, meta);
  }
  return withMeta(await res.json().catch(() => null), meta);
}

export async function getEmailNotifications(userId) {
  const { res, meta } = await safeFetch(`${BASE}/api/account/email-notifications/${userId}`, { headers: authHeaders() });
  if (!res) {
    return withMeta(null, meta);
  }
  return withMeta(await res.json().catch(() => null), meta);
}

export async function setEmailNotifications(userId, enabled) {
  const { res, meta } = await safeFetch(`${BASE}/api/account/email-notifications`, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...authHeaders() },
    body: JSON.stringify({ userId, enabled })
  });
  if (!res) {
    return withMeta(null, meta);
  }
  return withMeta(await res.json().catch(() => null), meta);
}

export async function setAccentColor(userId, accentColor) {
  const { res, meta } = await safeFetch(`${BASE}/api/account/accent-color`, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...authHeaders() },
    body: JSON.stringify({ userId, accentColor })
  });
  if (!res) {
    return withMeta(null, meta);
  }
  return withMeta(await res.json().catch(() => null), meta);
}

export async function setProfileTheme(userId, profileTheme) {
  const { res, meta } = await safeFetch(`${BASE}/api/account/profile-theme`, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...authHeaders() },
    body: JSON.stringify({ userId, profileTheme })
  });
  if (!res) {
    return withMeta(null, meta);
  }
  return withMeta(await res.json().catch(() => null), meta);
}

export async function setAvatar(userId, avatarUrl) {
  const { res, meta } = await safeFetch(`${BASE}/api/account/avatar`, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...authHeaders() },
    body: JSON.stringify({ userId, avatarUrl })
  });
  if (!res) {
    return withMeta(null, meta);
  }
  return withMeta(await res.json().catch(() => null), meta);
}

export async function setAvatarFrame(userId, frame) {
  const { res, meta } = await safeFetch(`${BASE}/api/account/avatar-frame`, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...authHeaders() },
    body: JSON.stringify({ userId, frame })
  });
  if (!res) {
    return withMeta(null, meta);
  }
  return withMeta(await res.json().catch(() => null), meta);
}

export async function setPinnedContent(userId, titleIds, ratingId, folderId) {
  const { res, meta } = await safeFetch(`${BASE}/api/account/pinned`, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...authHeaders() },
    body: JSON.stringify({ userId, titleIds, ratingId, folderId })
  });
  if (!res) {
    return withMeta(null, meta);
  }
  return withMeta(await res.json().catch(() => null), meta);
}

export async function setBio(userId, bio) {
  const { res, meta } = await safeFetch(`${BASE}/api/account/bio`, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...authHeaders() },
    body: JSON.stringify({ userId, bio })
  });
  if (!res) {
    return withMeta(null, meta);
  }
  return withMeta(await res.json().catch(() => null), meta);
}

export async function setProfileSong(userId, profileSong) {
  const { res, meta } = await safeFetch(`${BASE}/api/account/profile-song`, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...authHeaders() },
    body: JSON.stringify({ userId, profileSong })
  });
  if (!res) {
    return withMeta(null, meta);
  }
  return withMeta(await res.json().catch(() => null), meta);
}

export async function setNickname(userId, nickname) {
  const { res, meta } = await safeFetch(`${BASE}/api/account/nickname`, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...authHeaders() },
    body: JSON.stringify({ userId, nickname })
  });
  if (!res) {
    return withMeta(null, meta);
  }
  return withMeta(await res.json().catch(() => null), meta);
}

export async function deleteAccount(userId, password) {
  const { res, meta } = await safeFetch(`${BASE}/api/account/delete`, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...authHeaders() },
    body: JSON.stringify({ userId, password })
  });
  if (!res) {
    return withMeta(null, meta);
  }
  return withMeta(await res.json().catch(() => null), meta);
}

export async function getNotifications(userId) {
  const { res, meta } = await safeFetch(`${BASE}/api/notifications/${userId}`, { headers: authHeaders() });
  if (!res || !res.ok) {
    return withMeta([], meta);
  }
  return withMeta(await res.json(), meta);
}

export async function getUnreadNotificationCount(userId) {
  const { res } = await safeFetch(`${BASE}/api/notifications/${userId}/unread-count`, { headers: authHeaders() });
  // Returns a bare number — a primitive can't carry the hidden API_META
  // tag, so this one function can't distinguish states without changing
  // its return type. Left as a disclosed gap rather than forced into the
  // envelope; every other function on this page can carry the tag.
  if (!res || !res.ok) {
    return 0;
  }
  const data = await res.json().catch(() => null);
  return data?.count ?? 0;
}

export async function markAllNotificationsRead(userId) {
  // Already network-safe: the whole chain (fetch, not just .json()) is
  // caught, unlike every other function in this file before this pass.
  await fetch(`${BASE}/api/notifications/${userId}/read-all`, {
    method: "POST",
    headers: authHeaders()
  }).catch(() => null);
}

export async function searchUsers(query) {
  const { res, meta } = await safeFetch(`${BASE}/api/social/search?query=${encodeURIComponent(query)}`, { headers: authHeaders() });
  if (!res || !res.ok) {
    return withMeta([], meta);
  }
  return withMeta(await res.json(), meta);
}

export async function followUser(userId) {
  const { res, meta } = await safeFetch(`${BASE}/api/social/follow/${userId}`, {
    method: "POST",
    headers: authHeaders()
  });
  if (!res) {
    return withMeta(null, meta);
  }
  return withMeta(await res.json().catch(() => null), meta);
}

export async function unfollowUser(userId) {
  const { res, meta } = await safeFetch(`${BASE}/api/social/follow/${userId}`, {
    method: "DELETE",
    headers: authHeaders()
  });
  if (!res) {
    return withMeta(null, meta);
  }
  return withMeta(await res.json().catch(() => null), meta);
}

export async function discoverCollections(limit = 20) {
  const { res, meta } = await safeFetch(`${BASE}/api/social/collections/discover?limit=${limit}`, { headers: authHeaders() });
  if (!res || !res.ok) {
    return withMeta([], meta);
  }
  return withMeta(await res.json(), meta);
}

export async function getFollowedCollections() {
  const { res, meta } = await safeFetch(`${BASE}/api/social/collections/followed`, { headers: authHeaders() });
  if (!res || !res.ok) {
    return withMeta([], meta);
  }
  return withMeta(await res.json(), meta);
}

export async function followCollection(folderId) {
  const { res, meta } = await safeFetch(`${BASE}/api/social/collections/${folderId}/follow`, {
    method: "POST",
    headers: authHeaders()
  });
  if (!res) {
    return withMeta(null, meta);
  }
  return withMeta(await res.json().catch(() => null), meta);
}

export async function unfollowCollection(folderId) {
  const { res, meta } = await safeFetch(`${BASE}/api/social/collections/${folderId}/follow`, {
    method: "DELETE",
    headers: authHeaders()
  });
  if (!res) {
    return withMeta(null, meta);
  }
  return withMeta(await res.json().catch(() => null), meta);
}

export async function saveToWatchlist(userId, { tmdbId, titleId, title, folderId }) {
  const { res, meta } = await safeFetch(`${BASE}/api/watchlist/items`, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...authHeaders() },
    body: JSON.stringify({ userId, tmdbId, titleId, title, folderId })
  });
  if (!res) {
    return withMeta(null, meta);
  }
  return withMeta(await res.json().catch(() => null), meta);
}

export async function toggleReviewLike(ratingId) {
  const { res, meta } = await safeFetch(`${BASE}/api/reviews/${ratingId}/like`, {
    method: "POST",
    headers: authHeaders()
  });
  if (!res) {
    return withMeta(null, meta);
  }
  return withMeta(await res.json().catch(() => null), meta);
}

export async function getReviewComments(ratingId) {
  const { res, meta } = await safeFetch(`${BASE}/api/reviews/${ratingId}/comments`, { headers: authHeaders() });
  if (!res || !res.ok) {
    return withMeta([], meta);
  }
  return withMeta(await res.json(), meta);
}

export async function addReviewComment(ratingId, body, hasSpoilers) {
  const { res, meta } = await safeFetch(`${BASE}/api/reviews/${ratingId}/comments`, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...authHeaders() },
    body: JSON.stringify({ body, hasSpoilers: !!hasSpoilers })
  });
  if (!res) {
    return withMeta(null, meta);
  }
  return withMeta(await res.json().catch(() => null), meta);
}

export async function deleteReviewComment(commentId) {
  const { res, meta } = await safeFetch(`${BASE}/api/reviews/comments/${commentId}`, {
    method: "DELETE",
    headers: authHeaders()
  });
  if (!res) {
    return withMeta(null, meta);
  }
  return withMeta(await res.json().catch(() => null), meta);
}

export async function blockUser(userId) {
  const { res, meta } = await safeFetch(`${BASE}/api/social/block/${userId}`, {
    method: "POST",
    headers: authHeaders()
  });
  if (!res) {
    return withMeta(null, meta);
  }
  return withMeta(await res.json().catch(() => null), meta);
}

export async function unblockUser(userId) {
  const { res, meta } = await safeFetch(`${BASE}/api/social/block/${userId}`, {
    method: "DELETE",
    headers: authHeaders()
  });
  if (!res) {
    return withMeta(null, meta);
  }
  return withMeta(await res.json().catch(() => null), meta);
}

export async function getBlockedUsers() {
  const { res, meta } = await safeFetch(`${BASE}/api/social/blocked`, { headers: authHeaders() });
  if (!res || !res.ok) {
    return withMeta([], meta);
  }
  return withMeta(await res.json(), meta);
}

export async function fileReport(reportedUserId, reason, details, commentId) {
  const { res, meta } = await safeFetch(`${BASE}/api/reports`, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...authHeaders() },
    body: JSON.stringify({ reportedUserId, reason, details, commentId })
  });
  if (!res) {
    return withMeta(null, meta);
  }
  return withMeta(await res.json().catch(() => null), meta);
}

/** Admin-only — see AdminReports.jsx. 403s for a non-admin caller, same as any other /api/admin/** route. */
export async function listOpenReports() {
  const { res, meta } = await safeFetch(`${BASE}/api/admin/reports`, { headers: authHeaders() });
  if (!res || !res.ok) {
    return withMeta(null, meta);
  }
  return withMeta(await res.json(), meta);
}

export async function resolveReport(id) {
  const { res, meta } = await safeFetch(`${BASE}/api/admin/reports/${id}/resolve`, {
    method: "POST",
    headers: authHeaders()
  });
  if (!res) {
    return withMeta({ ok: false, data: null }, meta);
  }
  return withMeta({ ok: res.ok, data: await res.json().catch(() => null) }, meta);
}

export async function gettastedna(id) {
  const { res, meta } = await safeFetch(`${BASE}/api/tastedna/profile/${id}`, { headers: authHeaders() });
  if (!res || !res.ok) {
    return withMeta(null, meta);
  }
  return withMeta(await res.json(), meta);
}

export async function getAchievements(id) {
  const { res, meta } = await safeFetch(`${BASE}/api/tastedna/achievements/${id}`, { headers: authHeaders() });
  if (!res || !res.ok) {
    return withMeta(null, meta);
  }
  return withMeta(await res.json(), meta);
}

export async function searchmovies(query) {
  if (!query || !query.trim()) {
    const { res, meta } = await safeFetch(`${BASE}/api/movies/popular`, { headers: authHeaders() });
    if (!res || !res.ok) {
      return withMeta([], meta);
    }
    return withMeta(await res.json(), meta);
  }

  const { res, meta } = await safeFetch(`${BASE}/api/movies/search?query=${encodeURIComponent(query)}`, { headers: authHeaders() });
  if (!res || !res.ok) {
    return withMeta([], meta);
  }
  return withMeta(await res.json(), meta);
}
