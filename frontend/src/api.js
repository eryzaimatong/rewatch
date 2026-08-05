import { authHeaders } from "./auth";

export const BASE = import.meta.env.VITE_API_BASE ?? "http://localhost:8080";

export async function gettitles() {
  const res = await fetch(`${BASE}/api/titles`);
  if (!res.ok) {
    return [];
  }
  return await res.json();
}

export async function getrecs(id) {
  const res = await fetch(`${BASE}/api/recommendations/${id}`, { headers: authHeaders() });
  if (!res.ok) {
    return [];
  }
  return await res.json();
}

export async function login(user, pass) {
  const res = await fetch(`${BASE}/api/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username: user, password: pass })
  });
  // Parsed either way (not just on !res.ok) — the backend's error responses
  // carry a real `message` (e.g. "Invalid username/email or password") that
  // callers want to show, not just a generic failure.
  return await res.json().catch(() => null);
}

export async function register(user, pass, email) {
  // `email` is NOT NULL on the User entity, so omitting it made register 500.
  const res = await fetch(`${BASE}/api/auth/register`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      username: user,
      password: pass,
      email: email && email.trim() ? email.trim() : `${user}@rewatch.local`
    })
  });
  return await res.json().catch(() => null);
}

export async function forgotPassword(email) {
  const res = await fetch(`${BASE}/api/auth/forgot-password`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email })
  });
  return await res.json().catch(() => null);
}

export async function resetPassword(token, newPassword) {
  const res = await fetch(`${BASE}/api/auth/reset-password`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ token, newPassword })
  });
  return await res.json().catch(() => null);
}

export async function changePassword(userId, currentPassword, newPassword) {
  const res = await fetch(`${BASE}/api/account/change-password`, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...authHeaders() },
    body: JSON.stringify({ userId, currentPassword, newPassword })
  });
  return await res.json().catch(() => null);
}

export async function deleteAccount(userId, password) {
  const res = await fetch(`${BASE}/api/account/delete`, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...authHeaders() },
    body: JSON.stringify({ userId, password })
  });
  return await res.json().catch(() => null);
}

export async function getNotifications(userId) {
  const res = await fetch(`${BASE}/api/notifications/${userId}`, { headers: authHeaders() });
  if (!res.ok) {
    return [];
  }
  return await res.json();
}

export async function getUnreadNotificationCount(userId) {
  const res = await fetch(`${BASE}/api/notifications/${userId}/unread-count`, { headers: authHeaders() });
  if (!res.ok) {
    return 0;
  }
  const data = await res.json().catch(() => null);
  return data?.count ?? 0;
}

export async function markAllNotificationsRead(userId) {
  await fetch(`${BASE}/api/notifications/${userId}/read-all`, {
    method: "POST",
    headers: authHeaders()
  }).catch(() => null);
}

export async function blockUser(userId) {
  const res = await fetch(`${BASE}/api/social/block/${userId}`, {
    method: "POST",
    headers: authHeaders()
  });
  return await res.json().catch(() => null);
}

export async function unblockUser(userId) {
  const res = await fetch(`${BASE}/api/social/block/${userId}`, {
    method: "DELETE",
    headers: authHeaders()
  });
  return await res.json().catch(() => null);
}

export async function getBlockedUsers() {
  const res = await fetch(`${BASE}/api/social/blocked`, { headers: authHeaders() });
  if (!res.ok) {
    return [];
  }
  return await res.json();
}

export async function fileReport(reportedUserId, reason, details) {
  const res = await fetch(`${BASE}/api/reports`, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...authHeaders() },
    body: JSON.stringify({ reportedUserId, reason, details })
  });
  return await res.json().catch(() => null);
}

export async function gettastedna(id) {
  const res = await fetch(`${BASE}/api/tastedna/profile/${id}`, { headers: authHeaders() });
  if (!res.ok) {
    return null;
  }
  return await res.json();
}

export async function searchmovies(query) {
  if (!query || !query.trim()) {
    const res = await fetch(`${BASE}/api/movies/popular`, { headers: authHeaders() });
    if (!res.ok) {
      return [];
    }
    return await res.json();
  }

  const res = await fetch(`${BASE}/api/movies/search?query=${encodeURIComponent(query)}`, { headers: authHeaders() });
  if (!res.ok) {
    return [];
  }
  return await res.json();
}