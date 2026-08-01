const BASE = "http://localhost:8080";

export async function gettitles() {
  const res = await fetch(`${BASE}/api/titles`);
  if (!res.ok) {
    return [];
  }
  return await res.json();
}

export async function getrecs(id) {
  const res = await fetch(`${BASE}/api/recommendations/${id}`);
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
  if (!res.ok) {
    return null;
  }
  return await res.json();
}

export async function register(user, pass) {
  const res = await fetch(`${BASE}/api/auth/register`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username: user, password: pass })
  });
  if (!res.ok) {
    return null;
  }
  return await res.json();
}

export async function gettastedna(id) {
  // changed from /api/tastedna to /api/taste
  const res = await fetch(`${BASE}/api/taste/${id}`);
  if (!res.ok) {
    return null;
  }
  return await res.json();
}

export async function savetastedna(id, data) {
  const payload = {
    comfort: data.comfort,
    romance: data.romance,
    slowBurn: data.slowburn,
    slowburn: data.slowburn,
    nostalgia: data.nostalgia,
    sadness: data.sadness
  };

  // changed from /api/tastedna to /api/taste AND changed method from POST to PUT
  const res = await fetch(`${BASE}/api/taste/${id}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });
  return res.ok;
}

export async function searchmovies(query) {
  if (!query || !query.trim()) {
    const res = await fetch(`${BASE}/api/movies/popular`);
    if (!res.ok) {
      return [];
    }
    return await res.json();
  }

  const res = await fetch(`${BASE}/api/movies/search?query=${encodeURIComponent(query)}`);
  if (!res.ok) {
    return [];
  }
  return await res.json();
}