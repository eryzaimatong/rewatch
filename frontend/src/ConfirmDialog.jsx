import useModalA11y from "./useModalA11y";
import "./App.css";

/**
 * Reusable confirm-before-destructive-action dialog — reuses the same modal
 * shell as MovieModal (.modal-overlay/.modal-card--narrow/.modal-header) and
 * useModalA11y, rather than inventing a second dialog idiom. Used by
 * Dashboard's watchlist removal and Settings' delete-account flow.
 */
export default function ConfirmDialog({ title, message, confirmLabel = "Confirm", onConfirm, onCancel }) {
  const modalRef = useModalA11y(onCancel);

  return (
    <div
      className="modal-overlay"
      onClick={(e) => {
        if (e.target === e.currentTarget) {
          onCancel();
        }
      }}
    >
      <div
        className="modal-card modal-card--narrow"
        ref={modalRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby="confirm-dialog-title"
        tabIndex={-1}
      >
        <div className="modal-header">
          <h2 id="confirm-dialog-title" style={{ margin: 0 }}>{title}</h2>
          <button type="button" className="modal-close" onClick={onCancel} aria-label="Cancel">×</button>
        </div>
        <p style={{ color: "var(--text-muted)" }}>{message}</p>
        <div style={{ display: "flex", gap: "10px" }}>
          <button type="button" onClick={onCancel} className="btn-block">
            Cancel
          </button>
          <button type="button" onClick={onConfirm} className="btn-primary btn-block">
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}
