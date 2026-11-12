import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import useModalA11y from "./useModalA11y";
import "./App.css";

const COMMANDS = [
  { id: "home", label: "Home Feed", hint: "Tonight's pick, recommendations", path: "/" },
  { id: "watchlist", label: "My Watchlist", hint: "Your saved titles, Today's Pick", path: "/watchlist" },
  { id: "community", label: "Community", hint: "Similar TasteDNA, Collections", path: "/community" },
  { id: "profile", label: "TasteDNA Profile", hint: "Your storytelling vector, Roast My Taste", path: "/profile" },
  { id: "wrapped", label: "Wrapped", hint: "Your month in taste", path: "/wrapped" },
  { id: "achievements", label: "Achievements", hint: "Milestones and badges", path: "/achievements" },
  { id: "settings", label: "Settings", hint: "Account, appearance, privacy", path: "/settings" },
];

function score(command, query) {
  const q = query.toLowerCase();
  const label = command.label.toLowerCase();
  const idx = label.indexOf(q);
  if (idx === 0) return 0;
  if (idx > 0) return 10 + idx;
  if (command.hint.toLowerCase().includes(q)) return 50;
  return -1;
}

/**
 * Cmd/Ctrl+K anywhere in the authenticated app. Keyboard-first navigation
 * is table stakes for a product that wants to read as genuinely engineered
 * rather than merely styled.
 *
 * Split in two on purpose: this shell is mounted for the whole session and
 * only owns the open/closed state and the global shortcut. The dialog (and
 * with it useModalA11y's focus trap and body scroll lock) exists ONLY while
 * open. It used to be one component calling useModalA11y unconditionally, so
 * the scroll lock engaged on first paint and never released — every signed-in
 * page was unscrollable.
 */
export default function CommandPalette() {
  const [open, setopen] = useState(false);

  useEffect(() => {
    function onKeyDown(e) {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        setopen((v) => !v);
      }
    }
    function onOpenRequest() {
      setopen(true);
    }
    // The header's visible trigger button (App.jsx) fires this instead of
    // lifting open-state up a level — mouse users get a discoverable way in.
    window.addEventListener("keydown", onKeyDown);
    window.addEventListener("rewatch:open-command-palette", onOpenRequest);
    return () => {
      window.removeEventListener("keydown", onKeyDown);
      window.removeEventListener("rewatch:open-command-palette", onOpenRequest);
    };
  }, []);

  return open ? <PaletteDialog onClose={() => setopen(false)} /> : null;
}

function PaletteDialog({ onClose }) {
  const [query, setquery] = useState("");
  const [activeIndex, setactiveIndex] = useState(0);
  const navigate = useNavigate();
  const modalRef = useModalA11y(onClose);

  const results = query.trim()
    ? COMMANDS
        .map((c) => ({ c, s: score(c, query) }))
        .filter((r) => r.s >= 0)
        .sort((a, b) => a.s - b.s)
        .map((r) => r.c)
    : COMMANDS;

  function onQueryChange(value) {
    setquery(value);
    setactiveIndex(0);
  }

  function select(command) {
    if (!command) return;
    navigate(command.path);
    onClose();
  }

  function onKeyDown(e) {
    if (e.key === "ArrowDown") {
      e.preventDefault();
      setactiveIndex((i) => Math.min(i + 1, results.length - 1));
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      setactiveIndex((i) => Math.max(i - 1, 0));
    } else if (e.key === "Enter") {
      e.preventDefault();
      select(results[activeIndex]);
    }
  }

  return (
    <div
      className="command-palette-overlay"
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div
        className="command-palette"
        ref={modalRef}
        role="dialog"
        aria-modal="true"
        aria-label="Command palette"
        tabIndex={-1}
      >
        <input
          type="text"
          className="command-palette-input"
          placeholder="Jump to..."
          aria-label="Jump to a page"
          value={query}
          onChange={(e) => onQueryChange(e.target.value)}
          onKeyDown={onKeyDown}
          aria-activedescendant={results[activeIndex] ? `cmd-${results[activeIndex].id}` : undefined}
        />
        <div className="command-palette-list" role="listbox">
          {results.length === 0 && (
            <p className="command-palette-empty">No matches.</p>
          )}
          {results.map((c, i) => (
            <button
              key={c.id}
              id={`cmd-${c.id}`}
              type="button"
              role="option"
              aria-selected={i === activeIndex}
              className={`command-palette-item${i === activeIndex ? " is-active" : ""}`}
              onMouseEnter={() => setactiveIndex(i)}
              onClick={() => select(c)}
            >
              <span className="command-palette-item-label">{c.label}</span>
              <span className="command-palette-item-hint">{c.hint}</span>
            </button>
          ))}
        </div>
        <div className="command-palette-footer">
          <span><kbd>↑</kbd><kbd>↓</kbd> navigate</span>
          <span><kbd>↵</kbd> select</span>
          <span><kbd>esc</kbd> close</span>
        </div>
      </div>
    </div>
  );
}
