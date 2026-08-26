import { useEffect, useRef, useState } from "react";
import {
  changePassword, getEmail, changeEmail, deleteAccount, getBlockedUsers, unblockUser,
  getProfileVisibility, setProfileVisibility, getEmailNotifications, setEmailNotifications,
  setAccentColor, setAvatar,
  setAvatarFrame, getAchievements, setNickname, setBio, setProfileSong, setPinnedContent, setProfileTheme, BASE
} from "./api";
import { saveSession, clearSession, applyAccentColor, authHeaders } from "./auth";
import { getAccessibilityPrefs, setAccessibilityPref } from "./accessibility";
import { isSoundEnabled, setSoundEnabled, playConfirm } from "./sound";
import ConfirmDialog from "./ConfirmDialog";
import Avatar from "./Avatar";
import InstallAppButton from "./InstallAppButton";
import { IconLock } from "./Icons";
import "./App.css";

// Interactive-chrome accents only — the brand mark and TasteDNA radar
// intentionally keep their validated purple/cyan pairing (see User.java's
// accentColor javadoc). Swatch hexes mirror what App.css defines for each
// [data-accent] value.
const ACCENT_OPTIONS = [
  { key: "purple", label: "Purple", swatch: "#a855f7" },
  { key: "blue", label: "Blue", swatch: "#3b82f6" },
  { key: "orange", label: "Orange", swatch: "#f97316" },
  { key: "emerald", label: "Emerald", swatch: "#10b981" }
];

// A skin for your own /social/:id page only — see User.profileTheme. Swatch
// gradients mirror what App.css's [data-profile-theme] overrides actually paint.
const PROFILE_THEME_OPTIONS = [
  { key: "cinema", label: "Cinema", swatch: "linear-gradient(135deg, #14151a, #7e22ce)" },
  { key: "vhs", label: "VHS", swatch: "linear-gradient(135deg, #1a1425, #be185d)" },
  { key: "dreamy", label: "Dreamy", swatch: "linear-gradient(135deg, #2e1a47, #f0abfc)" },
  { key: "midnight", label: "Midnight", swatch: "linear-gradient(135deg, #020617, #1d4ed8)" },
  { key: "indie", label: "Indie", swatch: "linear-gradient(135deg, #d6c7a8, #92714f)" }
];

const AVATAR_TARGET_SIZE = 256;
const AVATAR_JPEG_QUALITY = 0.82;

// Mirrors AccountService.FRAME_REQUIREMENTS — the backend is the actual
// source of truth (and re-validates on every save), this is just what's
// needed to render locked/unlocked state without an extra round trip.
const FRAME_OPTIONS = [
  { key: "bronze", label: "Bronze", achievementKey: "rating_15", achievementTitle: "Well-Defined Profile" },
  { key: "silver", label: "Silver", achievementKey: "rating_30", achievementTitle: "Seasoned Critic" },
  { key: "gold", label: "Gold", achievementKey: "rating_100", achievementTitle: "Completionist" },
  { key: "signal", label: "Signal", achievementKey: "conf_90", achievementTitle: "TasteDNA Mastery" },
  { key: "social", label: "Social", achievementKey: "first_follower", achievementTitle: "Found by Someone" }
];

/** Center-crops to a square, then downscales — client-side, no upload of the original full-size photo. */
function resizeImageToDataUrl(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = (e) => {
      const img = new Image();
      img.onload = () => {
        const side = Math.min(img.width, img.height);
        const sx = (img.width - side) / 2;
        const sy = (img.height - side) / 2;
        const canvas = document.createElement("canvas");
        canvas.width = AVATAR_TARGET_SIZE;
        canvas.height = AVATAR_TARGET_SIZE;
        const ctx = canvas.getContext("2d");
        ctx.drawImage(img, sx, sy, side, side, 0, 0, AVATAR_TARGET_SIZE, AVATAR_TARGET_SIZE);
        resolve(canvas.toDataURL("image/jpeg", AVATAR_JPEG_QUALITY));
      };
      img.onerror = () => reject(new Error("Could not read that image."));
      img.src = e.target.result;
    };
    reader.onerror = () => reject(new Error("Could not read that file."));
    reader.readAsDataURL(file);
  });
}

export default function Settings() {
  const userid = Number(localStorage.getItem("userId")) || 1;

  const [currentpassword, setcurrentpassword] = useState("");
  const [newpassword, setnewpassword] = useState("");
  const [confirmpassword, setconfirmpassword] = useState("");
  const [pwloading, setpwloading] = useState(false);
  const [pwerr, setpwerr] = useState("");
  const [pwmsg, setpwmsg] = useState("");

  const [currentemail, setcurrentemail] = useState("");
  const [emailloading, setemailloading] = useState(true);
  const [newemail, setnewemail] = useState("");
  const [emailpassword, setemailpassword] = useState("");
  const [emailsaving, setemailsaving] = useState(false);
  const [emailerr, setemailerr] = useState("");
  const [emailmsg, setemailmsg] = useState("");

  const [deletepassword, setdeletepassword] = useState("");
  const [showconfirmdelete, setshowconfirmdelete] = useState(false);
  const [delerr, setdelerr] = useState("");
  const [delloading, setdelloading] = useState(false);

  const [blocked, setblocked] = useState([]);
  const [blockedloading, setblockedloading] = useState(true);
  const [unblockingId, setunblockingId] = useState(null);

  const [profilepublic, setprofilepublic] = useState(true);
  const [visibilityloading, setvisibilityloading] = useState(true);
  const [visibilitysaving, setvisibilitysaving] = useState(false);

  const [emailnotifsenabled, setemailnotifsenabled] = useState(true);
  const [emailnotifsloading, setemailnotifsloading] = useState(true);
  const [emailnotifssaving, setemailnotifssaving] = useState(false);

  const [accentcolor, setaccentcolor] = useState(localStorage.getItem("accentColor") || "purple");
  const [accentsaving, setaccentsaving] = useState(false);

  const [showconfirmretake, setshowconfirmretake] = useState(false);

  const [a11yprefs, seta11yprefs] = useState(() => getAccessibilityPrefs());
  function updatea11y(key, value) {
    setAccessibilityPref(key, value);
    seta11yprefs(getAccessibilityPrefs());
  }

  const [soundenabled, setsoundenabled] = useState(() => isSoundEnabled());
  function updatesound(enabled) {
    setSoundEnabled(enabled);
    setsoundenabled(enabled);
    // A little proof-of-life right when it's turned on, so the toggle
    // itself confirms what it just changed instead of leaving it to be
    // discovered (or not) on some later action.
    if (enabled) {
      playConfirm();
    }
  }

  const [avatarurl, setavatarurl] = useState(localStorage.getItem("avatarUrl") || null);
  const [avatarsaving, setavatarsaving] = useState(false);
  const [avatarerr, setavatarerr] = useState("");
  const avatarInputRef = useRef(null);
  const username = localStorage.getItem("username") || "You";

  const [avatarframe, setavatarframe] = useState(localStorage.getItem("avatarFrame") || null);
  const [unlockedachievementkeys, setunlockedachievementkeys] = useState(new Set());
  const [frameSavingKey, setframeSavingKey] = useState(null);

  const [nickname, setnicknameField] = useState("");
  const [nicknamesaving, setnicknamesaving] = useState(false);
  const [nicknamemsg, setnicknamemsg] = useState("");

  const [bio, setbioField] = useState("");
  const [biosaving, setbiosaving] = useState(false);
  const [biomsg, setbiomsg] = useState("");

  const [profilesong, setprofilesongField] = useState("");
  const [songsaving, setsongsaving] = useState(false);
  const [songmsg, setsongmsg] = useState("");

  const [profiletheme, setprofiletheme] = useState("cinema");
  const [themesaving, setthemesaving] = useState(false);

  const [ownreviews, setownreviews] = useState([]);
  const [ownfolders, setownfolders] = useState([]);
  const [pinnedtitleids, setpinnedtitleids] = useState([]);
  const [pinnedratingid, setpinnedratingid] = useState(null);
  const [pinnedfolderid, setpinnedfolderid] = useState(null);
  const [pinnedsaving, setpinnedsaving] = useState(false);
  const [pinnedmsg, setpinnedmsg] = useState("");

  async function loadnickname() {
    const res = await fetch(`${BASE}/api/social/profile/${userid}`, { headers: authHeaders() }).catch(() => null);
    if (res && res.ok) {
      const data = await res.json();
      setnicknameField(data.nickname || "");
      setbioField(data.bio || "");
      setprofilesongField(data.profileSong || "");
      setprofiletheme(data.profileTheme || "cinema");
      setpinnedtitleids((data.pinnedTitles || []).map((t) => t.titleId));
      setpinnedratingid(data.pinnedReview?.ratingId ?? null);
      setpinnedfolderid(data.pinnedCollection?.folderId ?? null);
    }
  }

  async function loadpinnedpickers() {
    const [reviewsRes, foldersRes] = await Promise.all([
      fetch(`${BASE}/api/social/${userid}/reviews?limit=40`, { headers: authHeaders() }).catch(() => null),
      fetch(`${BASE}/api/watchlist/${userid}/folders`, { headers: authHeaders() }).catch(() => null)
    ]);
    if (reviewsRes && reviewsRes.ok) setownreviews(await reviewsRes.json());
    if (foldersRes && foldersRes.ok) setownfolders(await foldersRes.json());
  }

  const MAX_PINNED_TITLES = 4;
  function togglepinnedtitle(titleId) {
    setpinnedtitleids((current) => {
      if (current.includes(titleId)) {
        return current.filter((id) => id !== titleId);
      }
      if (current.length >= MAX_PINNED_TITLES) {
        return current;
      }
      return [...current, titleId];
    });
  }

  async function handlesavepinned() {
    setpinnedsaving(true);
    setpinnedmsg("");
    const res = await setPinnedContent(userid, pinnedtitleids, pinnedratingid, pinnedfolderid);
    setpinnedmsg(res?.status === "success" ? "Saved." : res?.message || "Could not save your pinned content.");
    setpinnedsaving(false);
  }

  async function loadblocked() {
    setblockedloading(true);
    setblocked(await getBlockedUsers());
    setblockedloading(false);
  }

  async function loadachievements() {
    const res = await getAchievements(userid);
    if (res?.achievements) {
      setunlockedachievementkeys(new Set(res.achievements.filter((a) => a.unlocked).map((a) => a.key)));
    }
  }

  async function loadvisibility() {
    setvisibilityloading(true);
    const res = await getProfileVisibility(userid);
    if (res && typeof res.public === "boolean") {
      setprofilepublic(res.public);
    }
    setvisibilityloading(false);
  }

  async function loademailnotifs() {
    setemailnotifsloading(true);
    const res = await getEmailNotifications(userid);
    if (res && typeof res.enabled === "boolean") {
      setemailnotifsenabled(res.enabled);
    }
    setemailnotifsloading(false);
  }

  async function loademail() {
    setemailloading(true);
    const res = await getEmail(userid);
    if (res && res.email) {
      setcurrentemail(res.email);
    }
    setemailloading(false);
  }

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    loadblocked();
    loadvisibility();
    loademailnotifs();
    loademail();
    loadachievements();
    loadnickname();
    loadpinnedpickers();
  }, []);

  // The exact placeholder register()'s old no-email fallback used to write —
  // see AccountService.getEmail's own doc comment for why this pattern
  // reliably means "no real recovery email on file" rather than a coincidence.
  const hasNoRecoveryEmail = /^\d+@rewatch\.local$/.test(currentemail);

  async function handlechangeemail(e) {
    e.preventDefault();
    setemailerr("");
    setemailmsg("");

    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(newemail)) {
      setemailerr("Enter a valid email address.");
      return;
    }

    setemailsaving(true);
    const res = await changeEmail(userid, emailpassword, newemail);
    setemailsaving(false);

    if (res?.status === "success") {
      // Same reasoning as handlechangepassword below: changing email now
      // bumps the server-side tokenVersion too (a stolen-but-valid token
      // repointing account recovery to an attacker-controlled address
      // needs to actually end other sessions, not just this request), so
      // this session needs the freshly issued token or its own next
      // request 401s.
      saveSession({ token: res.token });
      setcurrentemail(newemail);
      setemailmsg("Email updated.");
      setnewemail("");
      setemailpassword("");
    } else {
      setemailerr(res?.message || "Could not update your email.");
    }
  }

  async function handlesavenickname() {
    setnicknamesaving(true);
    setnicknamemsg("");
    const res = await setNickname(userid, nickname.trim());
    if (res?.status === "success") {
      setnicknamemsg("Saved.");
    } else {
      setnicknamemsg(res?.message || "Could not save your nickname.");
    }
    setnicknamesaving(false);
  }

  async function handlesavebio() {
    setbiosaving(true);
    setbiomsg("");
    const res = await setBio(userid, bio.trim());
    if (res?.status === "success") {
      setbiomsg("Saved.");
    } else {
      setbiomsg(res?.message || "Could not save your bio.");
    }
    setbiosaving(false);
  }

  async function handlesavesong() {
    setsongsaving(true);
    setsongmsg("");
    const res = await setProfileSong(userid, profilesong.trim());
    if (res?.status === "success") {
      setsongmsg("Saved.");
    } else {
      setsongmsg(res?.message || "Could not save that.");
    }
    setsongsaving(false);
  }

  async function handletogglevisibility() {
    const next = !profilepublic;
    setvisibilitysaving(true);
    const res = await setProfileVisibility(userid, next);
    if (res?.status === "success") {
      setprofilepublic(next);
    }
    setvisibilitysaving(false);
  }

  async function handletoggleemailnotifs() {
    const next = !emailnotifsenabled;
    setemailnotifssaving(true);
    const res = await setEmailNotifications(userid, next);
    if (res?.status === "success") {
      setemailnotifsenabled(next);
    }
    setemailnotifssaving(false);
  }

  async function handleselectaccent(key) {
    if (key === accentcolor || accentsaving) return;
    setaccentsaving(true);
    const res = await setAccentColor(userid, key);
    if (res?.status === "success") {
      setaccentcolor(key);
      localStorage.setItem("accentColor", key);
      applyAccentColor(key);
    }
    setaccentsaving(false);
  }

  async function handleselecttheme(key) {
    if (key === profiletheme || themesaving) return;
    setthemesaving(true);
    const res = await setProfileTheme(userid, key);
    if (res?.status === "success") {
      setprofiletheme(key);
    }
    setthemesaving(false);
  }

  async function handleavatarfile(e) {
    const file = e.target.files?.[0];
    e.target.value = ""; // allow re-selecting the same file later
    if (!file) return;
    if (!file.type.startsWith("image/")) {
      setavatarerr("Please choose an image file.");
      return;
    }

    setavatarerr("");
    setavatarsaving(true);
    try {
      const dataUrl = await resizeImageToDataUrl(file);
      const res = await setAvatar(userid, dataUrl);
      if (res?.status === "success") {
        setavatarurl(dataUrl);
        localStorage.setItem("avatarUrl", dataUrl);
        window.dispatchEvent(new Event("rewatch:avatar-changed"));
      } else {
        setavatarerr(res?.message || "Could not save that photo.");
      }
    } catch {
      setavatarerr("Could not process that image.");
    }
    setavatarsaving(false);
  }

  async function handleremoveavatar() {
    setavatarerr("");
    setavatarsaving(true);
    const res = await setAvatar(userid, "");
    if (res?.status === "success") {
      setavatarurl(null);
      localStorage.removeItem("avatarUrl");
      window.dispatchEvent(new Event("rewatch:avatar-changed"));
    } else {
      setavatarerr(res?.message || "Could not remove your photo.");
    }
    setavatarsaving(false);
  }

  async function handleselectframe(frameKey) {
    // frameKey === avatarframe toggles it off (equip "none") — clicking your
    // currently-equipped frame again removes it, same affordance as most
    // "pick one of N, or none" pickers.
    const next = frameKey === avatarframe ? null : frameKey;
    setframeSavingKey(frameKey);
    const res = await setAvatarFrame(userid, next);
    if (res?.status === "success") {
      setavatarframe(next);
      if (next) {
        localStorage.setItem("avatarFrame", next);
      } else {
        localStorage.removeItem("avatarFrame");
      }
      window.dispatchEvent(new Event("rewatch:avatar-changed"));
    }
    setframeSavingKey(null);
  }

  function confirmretake() {
    setshowconfirmretake(false);
    // The onboarding screen itself submits to the same /api/movies/onboard
    // endpoint retaking would — it always overwrites seedVector and replays
    // the full rating log on top of it (see ProfileService.seedFromOnboarding),
    // so there's no separate "reset" call to make here. This just clears the
    // local flag and reloads so App.jsx's onboarded check (read once at
    // mount, not reactive to a same-tab localStorage write) re-evaluates to
    // false — same pattern Settings' own account-deletion flow already uses.
    localStorage.removeItem("onboarded");
    window.location.href = "/";
  }

  async function handleunblock(id) {
    setunblockingId(id);
    await unblockUser(id);
    setblocked((current) => current.filter((u) => u.userId !== id));
    setunblockingId(null);
  }

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
          <p className="section-eyebrow">Nickname</p>
          <p style={{ margin: "0 0 10px", color: "var(--text-muted)", fontSize: "0.88rem" }}>
            An expressive line shown under your @{username} on your profile — purely for personality, not used for login or search.
          </p>
          <div style={{ display: "flex", flexWrap: "wrap", gap: "10px", alignItems: "flex-start" }}>
            <input
              type="text"
              placeholder="the girl who cries at movies"
              value={nickname}
              onChange={(e) => setnicknameField(e.target.value)}
              maxLength={60}
              style={{ flex: "1 1 200px", minWidth: "160px", margin: 0 }}
            />
            <button type="button" className="btn-primary" onClick={handlesavenickname} disabled={nicknamesaving}>
              {nicknamesaving ? "..." : "Save"}
            </button>
          </div>
          {nicknamemsg && <p style={{ margin: "6px 0 0", fontSize: "0.8rem", color: "var(--text-faint)" }}>{nicknamemsg}</p>}
        </section>

        <section className="feed-section">
          <p className="section-eyebrow">Bio</p>
          <p style={{ margin: "0 0 10px", color: "var(--text-muted)", fontSize: "0.88rem" }}>
            A short line about you, shown on your profile.
          </p>
          <textarea
            rows={3}
            placeholder="Currently emotionally recovering from a 3-hour movie."
            value={bio}
            onChange={(e) => setbioField(e.target.value)}
            maxLength={200}
            style={{ width: "100%", resize: "vertical", marginBottom: "8px" }}
          />
          <div style={{ display: "flex", alignItems: "center", gap: "10px" }}>
            <button type="button" className="btn-primary" onClick={handlesavebio} disabled={biosaving}>
              {biosaving ? "..." : "Save"}
            </button>
            <span style={{ fontSize: "0.76rem", color: "var(--text-faint)" }}>{bio.length}/200</span>
            {biomsg && <span style={{ fontSize: "0.8rem", color: "var(--text-faint)" }}>{biomsg}</span>}
          </div>
        </section>

        <section className="feed-section">
          <p className="section-eyebrow">Profile song</p>
          <p style={{ margin: "0 0 10px", color: "var(--text-muted)", fontSize: "0.88rem" }}>
            Whatever represents your current cinematic era. Just text — no music service is connected.
          </p>
          <div style={{ display: "flex", gap: "10px" }}>
            <input
              type="text"
              placeholder="Get Him Back! — Olivia Rodrigo"
              value={profilesong}
              onChange={(e) => setprofilesongField(e.target.value)}
              maxLength={120}
              style={{ flex: 1, margin: 0 }}
            />
            <button type="button" className="btn-primary" onClick={handlesavesong} disabled={songsaving}>
              {songsaving ? "..." : "Save"}
            </button>
          </div>
          {songmsg && <p style={{ margin: "6px 0 0", fontSize: "0.8rem", color: "var(--text-faint)" }}>{songmsg}</p>}
        </section>

        <section className="feed-section">
          <p className="section-eyebrow">Profile picture</p>
          {avatarerr && <div className="status-message status-message--error">{avatarerr}</div>}
          <div style={{ display: "flex", alignItems: "center", gap: "var(--sp-3)" }}>
            <Avatar username={username} avatarUrl={avatarurl} avatarFrame={avatarframe} size={72} />
            <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
              <input
                ref={avatarInputRef}
                type="file"
                accept="image/*"
                onChange={handleavatarfile}
                style={{ display: "none" }}
              />
              <button
                type="button"
                className="btn-primary"
                style={{ maxWidth: "180px" }}
                onClick={() => avatarInputRef.current?.click()}
                disabled={avatarsaving}
              >
                {avatarsaving ? "..." : avatarurl ? "Change photo" : "Upload photo"}
              </button>
              {avatarurl && (
                <button
                  type="button"
                  className="auth-toggle-link"
                  style={{ fontSize: "0.8rem", background: "none", border: "none", textAlign: "left" }}
                  onClick={handleremoveavatar}
                  disabled={avatarsaving}
                >
                  Remove photo
                </button>
              )}
            </div>
          </div>
        </section>

        <section className="feed-section">
          <p className="section-eyebrow">Avatar frames</p>
          <p style={{ margin: "0 0 10px", color: "var(--text-muted)", fontSize: "0.88rem" }}>
            Collectible rings unlocked by real milestones — rate more, build up your TasteDNA confidence, or grow your network to earn one.
          </p>
          <div className="avatar-frame-picker">
            {FRAME_OPTIONS.map((f) => {
              const unlocked = unlockedachievementkeys.has(f.achievementKey);
              const equipped = avatarframe === f.key;
              return (
                <button
                  type="button"
                  key={f.key}
                  className={`avatar-frame-option${equipped ? " is-equipped" : ""}${!unlocked ? " is-locked" : ""}`}
                  onClick={() => unlocked && handleselectframe(f.key)}
                  disabled={!unlocked || frameSavingKey === f.key}
                  title={unlocked ? f.label : `Locked — unlock "${f.achievementTitle}" first`}
                >
                  <Avatar username={username} avatarUrl={avatarurl} avatarFrame={f.key} size={48} />
                  <span className="avatar-frame-option-label">{unlocked ? f.label : <IconLock />}</span>
                </button>
              );
            })}
          </div>
        </section>

        <section className="feed-section">
          <p className="section-eyebrow">Pinned content</p>
          <p style={{ margin: "0 0 10px", color: "var(--text-muted)", fontSize: "0.88rem" }}>
            Feature up to 4 titles you've actually rated, one written review, and one of your own lists at the top of your profile.
          </p>

          <p style={{ margin: "0 0 6px", fontSize: "0.8rem", fontWeight: 700 }}>
            Identity films ({pinnedtitleids.length}/{MAX_PINNED_TITLES})
          </p>
          {ownreviews.length === 0 ? (
            <p style={{ fontSize: "0.82rem", color: "var(--text-faint)" }}>Rate a few titles first to pin them here.</p>
          ) : (
            <div className="pinned-picker-grid">
              {ownreviews.map((r) => {
                const active = pinnedtitleids.includes(r.titleId);
                return (
                  <button
                    type="button"
                    key={r.ratingId}
                    className={`pinned-picker-title${active ? " is-selected" : ""}`}
                    onClick={() => togglepinnedtitle(r.titleId)}
                    title={r.title}
                  >
                    <img src={r.poster ? `https://image.tmdb.org/t/p/w200${r.poster}` : ""} alt="" loading="lazy" />
                    {active && <span className="pinned-picker-check">✓</span>}
                  </button>
                );
              })}
            </div>
          )}

          <p style={{ margin: "var(--sp-2) 0 6px", fontSize: "0.8rem", fontWeight: 700 }}>Pinned review</p>
          {ownreviews.length === 0 ? (
            <p style={{ fontSize: "0.82rem", color: "var(--text-faint)" }}>No written reviews yet.</p>
          ) : (
            <select
              value={pinnedratingid ?? ""}
              onChange={(e) => setpinnedratingid(e.target.value ? Number(e.target.value) : null)}
              style={{ marginBottom: "var(--sp-2)" }}
            >
              <option value="">None</option>
              {ownreviews.map((r) => (
                <option key={r.ratingId} value={r.ratingId}>{r.title} — {r.moment}</option>
              ))}
            </select>
          )}

          <p style={{ margin: "0 0 6px", fontSize: "0.8rem", fontWeight: 700 }}>Pinned list</p>
          {ownfolders.length === 0 ? (
            <p style={{ fontSize: "0.82rem", color: "var(--text-faint)" }}>You haven't made a list yet.</p>
          ) : (
            <select
              value={pinnedfolderid ?? ""}
              onChange={(e) => setpinnedfolderid(e.target.value ? Number(e.target.value) : null)}
              style={{ marginBottom: "var(--sp-2)" }}
            >
              <option value="">None</option>
              {ownfolders.map((f) => (
                <option key={f.id} value={f.id}>{f.name}</option>
              ))}
            </select>
          )}

          <div>
            <button type="button" className="btn-primary" onClick={handlesavepinned} disabled={pinnedsaving}>
              {pinnedsaving ? "..." : "Save pinned content"}
            </button>
            {pinnedmsg && <span style={{ marginLeft: "10px", fontSize: "0.8rem", color: "var(--text-faint)" }}>{pinnedmsg}</span>}
          </div>
        </section>

        <section className="feed-section">
          <p className="section-eyebrow">Recovery email</p>
          {!emailloading && hasNoRecoveryEmail && (
            <p className="status-message status-message--error" style={{ marginBottom: "10px" }}>
              No recovery email on file — if you forget your password, there's currently no way to get back in.
              Add a real email below.
            </p>
          )}
          {!emailloading && !hasNoRecoveryEmail && (
            <p style={{ margin: "0 0 10px", color: "var(--text-muted)", fontSize: "0.88rem" }}>
              Current: {currentemail}
            </p>
          )}
          {emailerr && <div className="status-message status-message--error">{emailerr}</div>}
          {emailmsg && <div className="status-message status-message--success">{emailmsg}</div>}

          <form onSubmit={handlechangeemail}>
            <label htmlFor="settings-new-email">New email</label>
            <input
              id="settings-new-email"
              type="email"
              value={newemail}
              onChange={(e) => setnewemail(e.target.value)}
              autoComplete="email"
              placeholder="you@example.com"
            />

            <label htmlFor="settings-email-password">Current password</label>
            <input
              id="settings-email-password"
              type="password"
              value={emailpassword}
              onChange={(e) => setemailpassword(e.target.value)}
              autoComplete="current-password"
            />

            <button type="submit" className="btn-primary" disabled={emailsaving}>
              {emailsaving ? "Saving..." : "Update Email"}
            </button>
          </form>
        </section>

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

        <InstallAppButton />

        <section className="feed-section">
          <p className="section-eyebrow">Appearance</p>
          <p style={{ margin: "0 0 10px", color: "var(--text-muted)", fontSize: "0.88rem" }}>
            Personalizes buttons, links, and active chips. Your TasteDNA chart stays purple either way.
          </p>
          <div className="accent-swatch-row">
            {ACCENT_OPTIONS.map((opt) => (
              <button
                key={opt.key}
                type="button"
                className={`accent-swatch${accentcolor === opt.key ? " is-selected" : ""}`}
                style={{ background: opt.swatch }}
                onClick={() => handleselectaccent(opt.key)}
                disabled={accentsaving}
                aria-label={`${opt.label} accent${accentcolor === opt.key ? " (selected)" : ""}`}
                title={opt.label}
              >
                {accentcolor === opt.key && <span aria-hidden="true">✓</span>}
              </button>
            ))}
          </div>
        </section>

        <section className="feed-section">
          <p className="section-eyebrow">Profile theme</p>
          <p style={{ margin: "0 0 10px", color: "var(--text-muted)", fontSize: "0.88rem" }}>
            A skin for your own profile page — what visitors see when they land on it. Doesn't change the rest of the app.
          </p>
          <div className="profile-theme-row">
            {PROFILE_THEME_OPTIONS.map((opt) => (
              <button
                type="button"
                key={opt.key}
                className={`profile-theme-swatch${profiletheme === opt.key ? " is-selected" : ""}`}
                style={{ background: opt.swatch }}
                onClick={() => handleselecttheme(opt.key)}
                disabled={themesaving}
                title={opt.label}
              >
                <span>{opt.label}</span>
                {profiletheme === opt.key && <span aria-hidden="true">✓</span>}
              </button>
            ))}
          </div>
        </section>

        <section className="feed-section">
          <p className="section-eyebrow">Accessibility</p>
          <p style={{ margin: "0 0 10px", color: "var(--text-muted)", fontSize: "0.88rem" }}>
            Applies on this device only — not synced to your account.
          </p>

          <p style={{ margin: "0 0 6px", fontSize: "0.8rem", fontWeight: 700 }}>Text size</p>
          <div className="pill-row" style={{ marginBottom: "var(--sp-2)" }}>
            {[["default", "Default"], ["large", "Large"], ["larger", "Larger"]].map(([key, label]) => (
              <button
                key={key}
                type="button"
                className={`pill${a11yprefs.fontSize === key ? " active" : ""}`}
                onClick={() => updatea11y("fontSize", key)}
              >
                {label}
              </button>
            ))}
          </div>

          <p style={{ margin: "0 0 6px", fontSize: "0.8rem", fontWeight: 700 }}>Contrast</p>
          <div className="pill-row" style={{ marginBottom: "var(--sp-2)" }}>
            {[["default", "Default"], ["high", "High contrast"]].map(([key, label]) => (
              <button
                key={key}
                type="button"
                className={`pill${a11yprefs.contrast === key ? " active" : ""}`}
                onClick={() => updatea11y("contrast", key)}
              >
                {label}
              </button>
            ))}
          </div>

          <p style={{ margin: "0 0 6px", fontSize: "0.8rem", fontWeight: 700 }}>Motion</p>
          <div className="pill-row">
            {[["default", "Default"], ["reduced", "Reduce motion"]].map(([key, label]) => (
              <button
                key={key}
                type="button"
                className={`pill${a11yprefs.motion === key ? " active" : ""}`}
                onClick={() => updatea11y("motion", key)}
              >
                {label}
              </button>
            ))}
          </div>

          <p style={{ margin: "var(--sp-2) 0 6px", fontSize: "0.8rem", fontWeight: 700 }}>Sound</p>
          <p style={{ margin: "0 0 6px", color: "var(--text-muted)", fontSize: "0.82rem" }}>
            A few small sounds — a rating landing, TasteDNA's reveal. Off by default.
          </p>
          <div className="pill-row">
            {[[false, "Off"], [true, "On"]].map(([value, label]) => (
              <button
                key={label}
                type="button"
                className={`pill${soundenabled === value ? " active" : ""}`}
                onClick={() => updatesound(value)}
              >
                {label}
              </button>
            ))}
          </div>
        </section>

        <section className="feed-section">
          <p className="section-eyebrow">TasteDNA</p>
          <p style={{ margin: "0 0 10px", color: "var(--text-muted)", fontSize: "0.88rem" }}>
            Redo the 5-step onboarding to reset your starting point. Your rating history isn't touched —
            it's replayed on top of your new answers, not lost.
          </p>
          <button type="button" className="btn-block" onClick={() => setshowconfirmretake(true)}>
            Retake TasteDNA Onboarding
          </button>
        </section>

        <section className="feed-section">
          <p className="section-eyebrow">Profile visibility</p>
          {visibilityloading ? (
            <p style={{ color: "var(--text-muted)", fontSize: "0.88rem" }}>Loading...</p>
          ) : (
            <div className="trait-card settings-toggle-row">
              <p style={{ margin: 0, color: "var(--text-muted)", fontSize: "0.88rem" }}>
                {profilepublic
                  ? "Public: anyone can view your profile, reviews, and TasteDNA."
                  : "Private: only you can view your profile, reviews, and TasteDNA."}
              </p>
              <button
                type="button"
                className={profilepublic ? "btn-primary" : "btn-block"}
                style={{ maxWidth: "140px" }}
                onClick={handletogglevisibility}
                disabled={visibilitysaving}
              >
                {visibilitysaving ? "..." : profilepublic ? "Make Private" : "Make Public"}
              </button>
            </div>
          )}
        </section>

        <section className="feed-section">
          <p className="section-eyebrow">Email notifications</p>
          {emailnotifsloading ? (
            <p style={{ color: "var(--text-muted)", fontSize: "0.88rem" }}>Loading...</p>
          ) : (
            <div className="trait-card settings-toggle-row">
              <p style={{ margin: 0, color: "var(--text-muted)", fontSize: "0.88rem" }}>
                {emailnotifsenabled
                  ? "On: we'll email you when someone new follows you."
                  : "Off: new-follower activity only shows up in-app."}
              </p>
              <button
                type="button"
                className={emailnotifsenabled ? "btn-primary" : "btn-block"}
                style={{ maxWidth: "140px" }}
                onClick={handletoggleemailnotifs}
                disabled={emailnotifssaving}
              >
                {emailnotifssaving ? "..." : emailnotifsenabled ? "Turn Off" : "Turn On"}
              </button>
            </div>
          )}
        </section>

        <section className="feed-section">
          <p className="section-eyebrow">Blocked users</p>
          {blockedloading ? (
            <p style={{ color: "var(--text-muted)", fontSize: "0.88rem" }}>Loading...</p>
          ) : blocked.length === 0 ? (
            <p style={{ color: "var(--text-muted)", fontSize: "0.88rem" }}>You haven't blocked anyone.</p>
          ) : (
            <div className="trait-list">
              {blocked.map((u) => (
                <div key={u.userId} className="trait-card settings-toggle-row">
                  <span className="trait-name">{u.username}</span>
                  <button
                    type="button"
                    className="btn-block"
                    style={{ maxWidth: "120px", padding: "8px 14px" }}
                    onClick={() => handleunblock(u.userId)}
                    disabled={unblockingId === u.userId}
                  >
                    {unblockingId === u.userId ? "..." : "Unblock"}
                  </button>
                </div>
              ))}
            </div>
          )}
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

      {showconfirmretake && (
        <ConfirmDialog
          title="Retake your TasteDNA onboarding?"
          message="You'll go through the 5-step onboarding again to reset your starting point. Your rating history stays intact — it's replayed on top of the new answers, not deleted."
          confirmLabel="Retake Onboarding"
          onConfirm={confirmretake}
          onCancel={() => setshowconfirmretake(false)}
        />
      )}

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
