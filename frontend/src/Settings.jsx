import { useState } from "react";
import { changePassword, deleteAccount } from "./api";
import { saveSession, clearSession } from "./auth";
import ConfirmDialog from "./ConfirmDialog";
import "./App.css";

export default function Settings() {
  const userid = Number(localStorage.getItem("userId")) || 1;

  const [currentpassword, setcurrentpassword] = useState("");
  const [newpassword, setnewpassword] = useState("");
  const [confirmpassword, setconfirmpassword] = useState("");
  const [pwloading, setpwloading] = useState(false);
  const [pwerr, setpwerr] = useState("");
  const [pwmsg, setpwmsg] = useState("");

  const [deletepassword, setdeletepassword] = useState("");
  const [showconfirmdelete, setshowconfirmdelete] = useState(false);
  const [delerr, setdelerr] = useState("");
  const [delloading, setdelloading] = useState(false);

  async function handlechangepassword(e) {
    e.preventDefault();
    setpwerr("");
    setpwmsg("");

    if (newpassword.length < 6) {
      setpwerr("New password must be at least 6 characters.");
      return;
    }
    if (newpassword !== confirmpassword) {
      setpwerr("New passwords don't match.");
      return;
    }

    setpwloading(true);
    const res = await changePassword(userid, currentpassword, newpassword);
    setpwloading(false);

    if (res?.status === "success") {
      // The old token is now invalid (changing password bumps the server-side
      // tokenVersion) — the response carries a freshly issued one so this
      // session doesn't get logged out by its own password change.
      saveSession({ token: res.token });
      setpwmsg("Password changed.");
      setcurrentpassword("");
      setnewpassword("");
      setconfirmpassword("");
    } else {
      setpwerr(res?.message || "Could not change your password.");
    }
  }

  function requestdelete() {
    setdelerr("");
    if (!deletepassword) {
      setdelerr("Enter your password to confirm.");
      return;
    }
    setshowconfirmdelete(true);
  }

  async function confirmdelete() {
    setshowconfirmdelete(false);
    setdelloading(true);
    const res = await deleteAccount(userid, deletepassword);
    setdelloading(false);

    if (res?.status === "success") {
      clearSession();
      window.location.href = "/";
    } else {
      setdelerr(res?.message || "Could not delete your account.");
    }
  }

  return (
    <div className="page-shell">
      <div className="page-panel taste-panel">
        <span className="eyebrow">Account</span>
        <h1>Settings</h1>

        <section className="feed-section">
          <p className="section-eyebrow">Change password</p>
          {pwerr && <div className="status-message status-message--error">{pwerr}</div>}
          {pwmsg && <div className="status-message status-message--success">{pwmsg}</div>}

          <form onSubmit={handlechangepassword}>
            <label htmlFor="settings-current-password">Current password</label>
            <input
              id="settings-current-password"
              type="password"
              value={currentpassword}
              onChange={(e) => setcurrentpassword(e.target.value)}
              autoComplete="current-password"
            />

            <label htmlFor="settings-new-password">New password</label>
            <input
              id="settings-new-password"
              type="password"
              value={newpassword}
              onChange={(e) => setnewpassword(e.target.value)}
              autoComplete="new-password"
            />

            <label htmlFor="settings-confirm-password">Confirm new password</label>
            <input
              id="settings-confirm-password"
              type="password"
              value={confirmpassword}
              onChange={(e) => setconfirmpassword(e.target.value)}
              autoComplete="new-password"
            />

            <button type="submit" className="btn-primary" disabled={pwloading}>
              {pwloading ? "Saving..." : "Change Password"}
            </button>
          </form>
        </section>

        <section className="feed-section">
          <p className="section-eyebrow" style={{ color: "#f87171" }}>Danger zone</p>
          {delerr && <div className="status-message status-message--error">{delerr}</div>}
          <p style={{ color: "var(--text-muted)", fontSize: "0.88rem" }}>
            Deleting your account permanently removes your ratings, watchlist, taste profile,
            and social connections. This can't be undone.
          </p>

          <label htmlFor="settings-delete-password">Enter your password to confirm</label>
          <input
            id="settings-delete-password"
            type="password"
            value={deletepassword}
            onChange={(e) => setdeletepassword(e.target.value)}
            autoComplete="current-password"
          />

          <button type="button" className="btn-block" onClick={requestdelete} disabled={delloading}
                  style={{ borderColor: "#f87171", color: "#f87171" }}>
            {delloading ? "Deleting..." : "Delete Account"}
          </button>
        </section>
      </div>

      {showconfirmdelete && (
        <ConfirmDialog
          title="Delete your account?"
          message="This permanently removes your ratings, watchlist, taste profile, and social connections. This can't be undone."
          confirmLabel="Delete Account"
          onConfirm={confirmdelete}
          onCancel={() => setshowconfirmdelete(false)}
        />
      )}
    </div>
  );
}
