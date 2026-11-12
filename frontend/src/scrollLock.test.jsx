import { describe, it, expect, afterEach } from "vitest";
import { createRoot } from "react-dom/client";
import { act } from "react";
import { MemoryRouter } from "react-router-dom";
import useModalA11y from "./useModalA11y";
import CommandPalette from "./CommandPalette";

globalThis.IS_REACT_ACT_ENVIRONMENT = true;

function Modal({ onClose }) {
  const ref = useModalA11y(onClose);
  return (
    <div ref={ref} role="dialog" tabIndex={-1}>
      <button type="button">ok</button>
    </div>
  );
}

let roots = [];
function mount(node) {
  const el = document.createElement("div");
  document.body.appendChild(el);
  const root = createRoot(el);
  act(() => root.render(node));
  roots.push({ root, el });
  return { root, el };
}

afterEach(() => {
  roots.forEach(({ root, el }) => {
    act(() => root.unmount());
    el.remove();
  });
  roots = [];
  document.body.style.overflow = "";
});

describe("body scroll lock", () => {
  it("restores scrolling when overlapping modals close in a different order than they opened", () => {
    const a = mount(<Modal onClose={() => {}} />);
    expect(document.body.style.overflow).toBe("hidden");
    const b = mount(<Modal onClose={() => {}} />);
    expect(document.body.style.overflow).toBe("hidden");

    // Close the FIRST one first — the old implementation left the page
    // permanently locked after this sequence.
    act(() => a.root.unmount());
    expect(document.body.style.overflow).toBe("hidden");
    act(() => b.root.unmount());
    expect(document.body.style.overflow).toBe("");
    roots = [];
  });

  it("preserves an overflow value that was already set before any modal", () => {
    document.body.style.overflow = "scroll";
    const a = mount(<Modal onClose={() => {}} />);
    expect(document.body.style.overflow).toBe("hidden");
    act(() => a.root.unmount());
    expect(document.body.style.overflow).toBe("scroll");
    roots = [];
  });

  it("does NOT lock scrolling while the command palette is closed (regression: every page was unscrollable)", () => {
    mount(
      <MemoryRouter>
        <CommandPalette />
      </MemoryRouter>
    );
    expect(document.body.style.overflow).toBe("");
  });

  it("locks only while the palette is open, and releases on Escape", () => {
    mount(
      <MemoryRouter>
        <CommandPalette />
      </MemoryRouter>
    );
    act(() => {
      window.dispatchEvent(new KeyboardEvent("keydown", { key: "k", ctrlKey: true, bubbles: true }));
    });
    expect(document.querySelector('[role="dialog"]')).not.toBeNull();
    expect(document.body.style.overflow).toBe("hidden");

    act(() => {
      document.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape", bubbles: true }));
    });
    expect(document.querySelector('[role="dialog"]')).toBeNull();
    expect(document.body.style.overflow).toBe("");
  });
});
