// Session storage + the Authorization header every authenticated fetch needs.
// The backend now authorizes every {userId} endpoint against the caller's JWT
// (see SecurityUtil.requireSelf) rather than trusting a client-supplied id, so a
// request missing this header 401s instead of silently returning someone else's
// data — see the Phase 9 case study notes for why.

export function getToken() {
  return localStorage.getItem("token");
}

export function authHeaders() {
  const token = getToken();
  return token ? { Authorization: `Bearer ${token}` } : {};
}

export function saveSession({ userId, username, token, onboarded, accentColor, avatarUrl, avatarFrame }) {
  if (userId != null) localStorage.setItem("userId", userId);
  if (username != null) localStorage.setItem("username", username);
  if (token) localStorage.setItem("token", token);
  // Explicitly set (not just left over from a previous account in this browser) —
  // the server is the source of truth for whether onboarding already ran.
  if (onboarded !== undefined) {
    if (onboarded) {
      localStorage.setItem("onboarded", "yes");
    } else {
      localStorage.removeItem("onboarded");
    }
  }
  if (accentColor) {
    localStorage.setItem("accentColor", accentColor);
    applyAccentColor(accentColor);
  }
  // Same explicit-set-or-clear reasoning as onboarded above — a stale avatar
  // from a previous account in this browser must not leak onto this one.
  if (avatarUrl !== undefined) {
    if (avatarUrl) {
      localStorage.setItem("avatarUrl", avatarUrl);
    } else {
      localStorage.removeItem("avatarUrl");
    }
  }
  if (avatarFrame !== undefined) {
    if (avatarFrame) {
      localStorage.setItem("avatarFrame", avatarFrame);
    } else {
      localStorage.removeItem("avatarFrame");
    }
  }
}

/** Stamps the chosen accent on <html> — see App.css's `:root[data-accent=...]` overrides. */
export function applyAccentColor(accentColor) {
  document.documentElement.setAttribute("data-accent", accentColor || "purple");
}

export function clearSession() {
  localStorage.removeItem("userId");
  localStorage.removeItem("username");
  localStorage.removeItem("token");
  localStorage.removeItem("onboarded");
  localStorage.removeItem("accentColor");
  localStorage.removeItem("avatarUrl");
  localStorage.removeItem("avatarFrame");
  applyAccentColor(null);
}
