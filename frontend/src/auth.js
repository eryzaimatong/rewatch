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

export function saveSession({ userId, username, token }) {
  if (userId != null) localStorage.setItem("userId", userId);
  if (username != null) localStorage.setItem("username", username);
  if (token) localStorage.setItem("token", token);
}

export function clearSession() {
  localStorage.removeItem("userId");
  localStorage.removeItem("username");
  localStorage.removeItem("token");
  localStorage.removeItem("onboarded");
}
