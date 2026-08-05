import { useState } from "react";
import useModalA11y from "./useModalA11y";
import "./App.css";

const REASONS = [
  { value: "SPAM", label: "Spam" },
  { value: "HARASSMENT", label: "Harassment" },
  { value: "INAPPROPRIATE_CONTENT", label: "Inappropriate content" },
  { value: "IMPERSONATION", label: "Impersonation" },
  { value: "OTHER", label: "Other" }
];

/**
 * The one modal shape Stage 2 needs beyond ConfirmDialog — same shell
 * (.modal-overlay/.modal-card--narrow/.modal-header, useModalA11y), but with
 * a small form instead of a plain confirm.
 */
export default function ReportDialog({ username, onSubmit, onCancel, submitting }) {
  const [reason, setreason] = useState("SPAM");
  const [details, setdetails] = useState("");
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
        aria-labelledby="report-dialog-title"
        tabIndex={-1}
      >
        <div className="modal-header">
          <h2 id="report-dialog-title" style={{ margin: 0 }}>Report {username}</h2>
          <button type="button" className="modal-close" onClick={onCancel} aria-label="Cancel">×</button>
        </div>

        <label htmlFor="report-reason">Reason</label>
        <select id="report-reason" value={reason} onChange={(e) => setreason(e.target.value)}>
          {REASONS.map((r) => (
            <option key={r.value} value={r.value}>{r.label}</option>
          ))}
        </select>

        <label htmlFor="report-details">Details (optional)</label>
        <textarea
          id="report-details"
          rows={3}
          value={details}
          onChange={(e) => setdetails(e.target.value)}
          placeholder="Anything that would help us understand what happened"
          style={{ width: "100%", resize: "vertical" }}
        />

        <div style={{ display: "flex", gap: "10px", marginTop: "var(--sp-2)" }}>
          <button type="button" onClick={onCancel} className="btn-block">
            Cancel
          </button>
          <button
            type="button"
            onClick={() => onSubmit(reason, details.trim() || null)}
            className="btn-primary btn-block"
            disabled={submitting}
          >
            {submitting ? "Submitting..." : "Submit Report"}
          </button>
        </div>
      </div>
    </div>
  );
}
