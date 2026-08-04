import { useEffect, useRef } from "react";

const FOCUSABLE_SELECTOR =
  'a[href], button:not([disabled]), textarea:not([disabled]), input:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])';

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
    const focusable = container?.querySelectorAll(FOCUSABLE_SELECTOR);
    if (focusable && focusable.length > 0) {
      focusable[0].focus();
    } else {
      container?.focus();
    }

    function handleKeydown(e) {
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
