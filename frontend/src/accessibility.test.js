import { beforeEach, describe, expect, it } from "vitest";
import { applyAccessibilityPrefs, getAccessibilityPrefs, setAccessibilityPref } from "./accessibility";

describe("accessibility preferences", () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.removeAttribute("data-font-size");
    document.documentElement.removeAttribute("data-contrast");
    document.documentElement.removeAttribute("data-motion");
  });

  it("defaults to 'default' for every preference with nothing stored", () => {
    expect(getAccessibilityPrefs()).toEqual({
      fontSize: "default",
      contrast: "default",
      motion: "default"
    });
  });

  it("setting a non-default value stores it and reflects it as a root attribute", () => {
    setAccessibilityPref("fontSize", "large");
    expect(getAccessibilityPrefs().fontSize).toBe("large");
    expect(document.documentElement.getAttribute("data-font-size")).toBe("large");
  });

  it("setting back to 'default' clears storage and removes the attribute, not just sets it to the string 'default'", () => {
    setAccessibilityPref("contrast", "high");
    expect(document.documentElement.getAttribute("data-contrast")).toBe("high");

    setAccessibilityPref("contrast", "default");
    expect(localStorage.getItem("a11yContrast")).toBeNull();
    expect(document.documentElement.getAttribute("data-contrast")).toBeNull();
  });

  it("applyAccessibilityPrefs re-applies all three independently", () => {
    setAccessibilityPref("motion", "reduced");
    document.documentElement.removeAttribute("data-motion"); // simulate a fresh page load before boot re-applies it

    applyAccessibilityPrefs();

    expect(document.documentElement.getAttribute("data-motion")).toBe("reduced");
    expect(document.documentElement.hasAttribute("data-font-size")).toBe(false);
    expect(document.documentElement.hasAttribute("data-contrast")).toBe(false);
  });
});
