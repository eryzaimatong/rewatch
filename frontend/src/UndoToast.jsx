import { AnimatePresence, motion } from "framer-motion";
import "./App.css";

/**
 * Generalizes MovieModal's `.shift-toast` (inline-in-modal, no action button,
 * no auto-dismiss) into a standalone fixed-position toast with an Undo
 * action. Used for the optimistic-remove-then-confirm flow on watchlist items.
 */
export default function UndoToast({ message, onUndo }) {
  return (
    <AnimatePresence>
      <motion.div
        className="shift-toast undo-toast"
        initial={{ opacity: 0, y: 10, scale: 0.96 }}
        animate={{ opacity: 1, y: 0, scale: 1 }}
        exit={{ opacity: 0, y: -6 }}
        transition={{ type: "spring", stiffness: 340, damping: 22 }}
      >
        <span className="shift-toast-icon">↺</span>
        <span className="shift-toast-text">{message}</span>
        <button type="button" className="undo-toast-action" onClick={onUndo}>
          Undo
        </button>
      </motion.div>
    </AnimatePresence>
  );
}
