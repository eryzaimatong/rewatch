import { useEffect, useRef } from "react";

const FOCUSABLE_SELECTOR =
  'a[href], button:not([disabled]), textarea:not([disabled]), input:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])';

// Module-level, shared across every useModalA11y instance currently mounted
// — a plain array works as a stack because React mounts the child before
// the parent's effects have a chance to run again, so nesting order and
// mount order agree. Without this, a ConfirmDialog opened from inside
// MovieModal (both call useModalA11y, each with its own document-level
// Escape listener) closed BOTH on a single Escape press: neither listener
// knows the other exists, so both just fire. Verified live before this fix
// — Escape while "Delete your rating?" was open closed the confirm dialog
// AND the movie detail view behind it in one keypress.
const openModals = [];

/**
 * Everything a modal needs to not be a keyboard/screen-reader trap: Escape
 * closes it, Tab cycles only within it, the background can't scroll behind
 * it, and focus both lands inside it on open and returns to whatever
 * triggered it on close.
 *
 * Every modal in this app (MovieModal, the share-card modal) previously had
 * none of this — no Escape handler, no focus trap, no aria-modal, no scroll
 * lock — so keyboard and screen-reader users could tab straight into the
 * dimmed background content behind an "open" modal.
 *
 * Usage: const modalRef = useModalA11y(onClose); <div ref={modalRef} role="dialog" aria-modal="true">
 */
export default function useModalA11y(onClose) {
  const containerRef = useRef(null);
  const triggerElementRef = useRef(null);

  useEffect(() => {
    triggerElementRef.current = document.activeElement;

    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";

    const container = containerRef.current;
    openModals.push(container);

    const focusable = container?.querySelectorAll(FOCUSABLE_SELECTOR);
    if (focusable && focusable.length > 0) {
      focusable[0].focus();
    } else {
      container?.focus();
    }

    function handleKeydown(e) {
      // Only the most-recently-opened modal reacts — an outer modal's
      // listener is still attached and still fires (there's no
      // stopPropagation, and it wouldn't help anyway since both listeners
      // are on the same `document` target regardless of DOM nesting), it
      // just no-ops while it isn't the top of the stack.
      if (openModals[openModals.length - 1] !== container) {
        return;
      }
      if (e.key === "Escape") {
        onClose();
        return;
      }
      if (e.key !== "Tab" || !container) {
        return;
      }

      const nodes = Array.from(container.querySelectorAll(FOCUSABLE_SELECTOR)).filter(
        (el) => el.offsetParent !== null
      );
      if (nodes.length === 0) {
        return;
      }
      const first = nodes[0];
      const last = nodes[nodes.length - 1];

      if (e.shiftKey && document.activeElement === first) {
        e.preventDefault();
        last.focus();
      } else if (!e.shiftKey && document.activeElement === last) {
        e.preventDefault();
        first.focus();
      }
    }

    document.addEventListener("keydown", handleKeydown);

    return () => {
      const idx = openModals.indexOf(container);
      if (idx !== -1) {
        openModals.splice(idx, 1);
      }
      document.removeEventListener("keydown", handleKeydown);
      document.body.style.overflow = previousOverflow;
      // Restore focus to whatever opened the modal — a sighted mouse user
      // never notices this, but a keyboard user would otherwise land back
      // at the top of the document.
      if (triggerElementRef.current instanceof HTMLElement) {
        triggerElementRef.current.focus();
      }
    };
    // onClose is passed fresh from the caller's state setter each render,
    // which is stable enough here — re-running this on every render would
    // fight the focus trap it just set up.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return containerRef;
}
