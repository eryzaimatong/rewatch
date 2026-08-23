import { beforeEach, describe, expect, it } from "vitest";
import { authHeaders, clearSession, getToken, isAdmin, saveSession } from "./auth";

// The account-switching case is the one worth real coverage: saveSession's
// avatarUrl/avatarFrame/onboarded handling distinguishes "not passed"
// (leave whatever's there alone) from "passed as empty/false" (a previous
// account's browser data must not leak onto the next login in the same
// browser) — those are opposite behaviors for what looks like the same
// falsy value, exactly the kind of thing that's easy to silently invert.
describe("auth session storage", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it("authHeaders is empty with no token", () => {
    expect(authHeaders()).toEqual({});
  });

  it("authHeaders carries the bearer token once saved", () => {
    saveSession({ userId: 1, token: "abc123" });
    expect(getToken()).toBe("abc123");
    expect(authHeaders()).toEqual({ Authorization: "Bearer abc123" });
  });

  it("clears a previous account's avatar when the new session has none", () => {
    saveSession({ userId: 1, avatarUrl: "data:image/old", avatarFrame: "gold" });
    expect(localStorage.getItem("avatarUrl")).toBe("data:image/old");

    saveSession({ userId: 2, avatarUrl: "", avatarFrame: "" });
    expect(localStorage.getItem("avatarUrl")).toBeNull();
    expect(localStorage.getItem("avatarFrame")).toBeNull();
  });

  it("leaves the stored avatar alone when the field isn't part of this call at all", () => {
    saveSession({ userId: 1, avatarUrl: "data:image/keep-me" });
    saveSession({ userId: 1, token: "refreshed-token" }); // avatarUrl omitted, not cleared
    expect(localStorage.getItem("avatarUrl")).toBe("data:image/keep-me");
  });

  it("removes the onboarded flag rather than storing a false-y value", () => {
    saveSession({ userId: 1, onboarded: true });
    expect(localStorage.getItem("onboarded")).toBe("yes");

    saveSession({ userId: 1, onboarded: false });
    expect(localStorage.getItem("onboarded")).toBeNull();
  });

  it("clearSession wipes every session key", () => {
    saveSession({
      userId: 1, username: "eryza", token: "t", onboarded: true,
      accentColor: "blue", avatarUrl: "data:x", avatarFrame: "gold", role: "ADMIN"
    });
    clearSession();
    for (const key of ["userId", "username", "token", "onboarded", "accentColor", "avatarUrl", "avatarFrame", "role"]) {
      expect(localStorage.getItem(key)).toBeNull();
    }
  });

  it("isAdmin reflects the stored role, display-only per the server re-check", () => {
    expect(isAdmin()).toBe(false);
    saveSession({ userId: 1, role: "ADMIN" });
    expect(isAdmin()).toBe(true);
  });
});
