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