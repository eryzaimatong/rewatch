import { describe, it, expect, afterEach, vi } from "vitest";
import { createRoot } from "react-dom/client";
import { act } from "react";
import { MemoryRouter, Routes, Route } from "react-router-dom";
import { NightStart, NightRoom } from "./Night";

globalThis.IS_REACT_ACT_ENVIRONMENT = true;

const QUESTIONS = Array.from({ length: 5 }, (_, i) => ({ id: `q${i}`, prompt: `Question ${i}?`, options: ["A", "B"] }));
let roots = [];
function mount(node) {
  const el = document.createElement("div");
  document.body.appendChild(el);
  const root = createRoot(el);
  act(() => root.render(node));
  roots.push({ root, el });
  return el;
}
const flush = () => act(async () => { await new Promise((r) => setTimeout(r, 0)); });

afterEach(() => {
  roots.forEach(({ root, el }) => { act(() => root.unmount()); el.remove(); });
  roots = [];
  vi.restoreAllMocks();
  localStorage.clear();
});

function route(path, element, at) {
  return (
    <MemoryRouter initialEntries={[at]}>
      <Routes><Route path={path} element={element} /></Routes>
    </MemoryRouter>
  );
}

describe("Movie Night", () => {
  it("asks for a name, then walks through all five questions before offering to start", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => ({ ok: true, json: async () => QUESTIONS })));
    const el = mount(route("/night", <NightStart />, "/night"));
    await flush();
    expect(el.textContent).not.toContain("Question 0?");
    const input = el.querySelector("input");
    await act(async () => {
      const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value").set;
      setter.call(input, "Mira");
      input.dispatchEvent(new Event("input", { bubbles: true }));
    });
    for (let i = 0; i < 5; i++) {
      expect(el.textContent).toContain(`Question ${i}?`);
      await act(async () => el.querySelector(".night-option").click());
    }
    expect(el.textContent).toContain("All set, Mira.");
    expect(el.textContent).toContain("Start the night");
  });

  it("shows a friendly end state for an expired night", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => ({ ok: false, status: 404, json: async () => ({ message: "gone" }) })));
    const el = mount(route("/night/:code", <NightRoom />, "/night/ABCDEF"));
    await flush();
    expect(el.textContent).toContain("This movie night has ended.");
  });

  it("invites a newcomer to answer the quiz instead of showing results", async () => {
    vi.stubGlobal("fetch", vi.fn(async (url) => ({
      ok: true,
      json: async () => (String(url).endsWith("/questions")
        ? QUESTIONS
        : { code: "ABCDEF", maxMembers: 8, members: [{ id: 1, name: "Mira" }], picks: [] })
    })));
    const el = mount(route("/night/:code", <NightRoom />, "/night/ABCDEF"));
    await flush();
    await flush();
    expect(el.textContent).toContain("You’re invited to a Movie Night");
    expect(el.textContent).toContain("Mira started one");
  });
});
