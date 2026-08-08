import { useEffect, useState } from "react";

/**
 * Chrome/Edge/Android fire `beforeinstallprompt` only when the PWA criteria
 * are actually met (manifest + service worker + served over HTTPS) — this
 * component just captures that event and re-fires it on a real click, since
 * the browser requires the prompt() call happen inside a user gesture, not
 * automatically. iOS Safari never fires this event at all (Apple's install
 * flow is manual, Share -> Add to Home Screen), so this renders nothing
 * there rather than a button that would never do anything.
 */
export default function InstallAppButton() {
  const [deferredPrompt, setdeferredPrompt] = useState(null);
  const [installed, setinstalled] = useState(false);

  useEffect(() => {
    function handlebeforeinstall(e) {
      e.preventDefault();
      setdeferredPrompt(e);
    }
    function handleinstalled() {
      setinstalled(true);
      setdeferredPrompt(null);
    }
    window.addEventListener("beforeinstallprompt", handlebeforeinstall);
    window.addEventListener("appinstalled", handleinstalled);
    return () => {
      window.removeEventListener("beforeinstallprompt", handlebeforeinstall);
      window.removeEventListener("appinstalled", handleinstalled);
    };
  }, []);

  if (installed || !deferredPrompt) {
    return null;
  }

  async function handleinstall() {
    deferredPrompt.prompt();
    await deferredPrompt.userChoice;
    setdeferredPrompt(null);
  }

  return (
    <section className="feed-section">
      <p className="section-eyebrow">Get the app</p>
      <p style={{ margin: "0 0 10px", color: "var(--text-muted)", fontSize: "0.88rem" }}>
        Install Re:Watch for a full-screen, app-like experience — works offline for anything you've already loaded.
      </p>
      <button type="button" className="btn-primary" style={{ maxWidth: "220px" }} onClick={handleinstall}>
        Install Re:Watch
      </button>
    </section>
  );
}
