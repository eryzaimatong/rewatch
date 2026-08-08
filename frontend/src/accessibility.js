// Browser-local accessibility preferences — deliberately NOT account data
// (no backend field, no cross-device sync), same reasoning as the vibe/
// language-filter preferences in MovieFeed.jsx: these are about how this
// specific screen renders, not something meaningful to carry to a different
// device's different screen/eyesight/motion sensitivity.

const KEYS = {
  fontSize: "a11yFontSize",   // "default" | "large" | "larger"
  contrast: "a11yContrast",   // "default" | "high"
  motion: "a11yMotion"        // "default" | "reduced"
};

export function getAccessibilityPrefs() {
  return {
    fontSize: localStorage.getItem(KEYS.fontSize) || "default",
    contrast: localStorage.getItem(KEYS.contrast) || "default",
    motion: localStorage.getItem(KEYS.motion) || "default"
  };
}

function applyAttr(attr, value) {
  if (value && value !== "default") {
    document.documentElement.setAttribute(attr, value);
  } else {
    document.documentElement.removeAttribute(attr);
  }
}

/** Call once at boot, and again any time a preference changes. */
export function applyAccessibilityPrefs(prefs = getAccessibilityPrefs()) {
  applyAttr("data-font-size", prefs.fontSize);
  applyAttr("data-contrast", prefs.contrast);
  applyAttr("data-motion", prefs.motion);
}

export function setAccessibilityPref(key, value) {
  if (value === "default") {
    localStorage.removeItem(KEYS[key]);
  } else {
    localStorage.setItem(KEYS[key], value);
  }
  applyAccessibilityPrefs(getAccessibilityPrefs());
}
