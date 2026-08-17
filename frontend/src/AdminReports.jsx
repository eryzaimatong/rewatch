import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { listOpenReports, resolveReport } from "./api";
import EmptyState from "./EmptyState";
import ErrorState from "./ErrorState";
import "./App.css";

const REASON_LABELS = {
  SPAM: "Spam",
  HARASSMENT: "Harassment",
  INAPPROPRIATE_CONTENT: "Inappropriate content",
  IMPERSONATION: "Impersonation",
  OTHER: "Other"
};

function formatWhen(iso) {
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? "" : d.toLocaleString();
}

/**
 * The only way to act on a filed report used to be curl/Swagger against
 * GET/POST /api/admin/reports — fine while this was a single-operator
 * project with no real user base, not fine the moment a stranger files a
 * report and there's no UI to see it, let alone act on it. Route-gated
 * client-side (App.jsx checks isAdmin() before rendering this at all) purely
 * so a non-admin doesn't see a dead-end page; the actual authorization is
 * still the server-side hasRole("ADMIN") check this page's requests hit
 * regardless.
 */
export default function AdminReports() {
  const [reports, setreports] = useState(null);
  const [loading, setloading] = useState(true);
  const [err, seterr] = useState("");
  const [resolvingId, setresolvingId] = useState(null);

  async function load() {
    setloading(true);
    seterr("");
    const res = await listOpenReports();
    if (res) {
      setreports(res);
    } else {
      seterr("Could not load reports right now.");
    }
    setloading(false);
  }

  useEffect(() => {
    // See MovieModal.jsx/EvolutionTimeline.jsx for why this needs a targeted
    // disable: a fetch-on-mount is exactly what an effect is for, even
    // though the rule flags any effect that sets state before its async
    // call resolves.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    load();
  }, []);

  async function handleResolve(id) {
    setresolvingId(id);
    const { ok } = await resolveReport(id);
    if (ok) {
      setreports((prev) => prev.filter((r) => r.id !== id));
    }
    setresolvingId(null);
  }

  return (
    <div className="page-shell page-shell--wide">
      <div className="page-panel taste-panel">
        <div className="taste-panel-head">
          <div>
            <span className="eyebrow">Admin</span>
            <h1>{reports ? `${reports.length} open report${reports.length === 1 ? "" : "s"}` : "Reports"}</h1>
          </div>
        </div>

        {loading && (
          <div className="feed-state">
            <div className="loading-orb" />
            <p>Loading reports...</p>
          </div>
        )}

        {!loading && err && <ErrorState message={err} onRetry={load} />}

        {!loading && !err && reports && reports.length === 0 && (
          <EmptyState title="Nothing open" message="No unresolved reports right now." />
        )}

        {!loading && !err && reports && reports.length > 0 && (
          <div className="admin-report-list">
            {reports.map((r) => (
              <div key={r.id} className="admin-report-card">
                <div className="admin-report-card-head">
                  <span className="admin-report-reason">{REASON_LABELS[r.reason] || r.reason}</span>
                  <span className="admin-report-time">{formatWhen(r.createdAt)}</span>
                </div>
                <p className="admin-report-line">
                  Reported: <Link to={`/social/${r.reportedUserId}`}>user #{r.reportedUserId}</Link>
                  {" · "}
                  Filed by: <Link to={`/social/${r.reporterId}`}>user #{r.reporterId}</Link>
                  {r.commentId != null && <> {" · "}comment #{r.commentId}</>}
                </p>
                {r.details && <p className="admin-report-details">"{r.details}"</p>}
                <button
                  type="button"
                  className="btn-secondary"
                  disabled={resolvingId === r.id}
                  onClick={() => handleResolve(r.id)}
                >
                  {resolvingId === r.id ? "Resolving…" : "Mark resolved"}
                </button>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
