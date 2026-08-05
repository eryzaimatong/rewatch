import "./App.css";

export default function TermsOfService() {
  return (
    <div className="page-shell">
      <div className="page-panel">
        <span className="eyebrow">Legal</span>
        <h1>Terms of Service</h1>
        <p style={{ color: "var(--text-faint)", fontSize: "0.82rem" }}>
          Effective date: [effective date]
        </p>

        <h2>Using Re:Watch</h2>
        <p>
          Re:Watch is an entertainment discovery app. You need an account to rate
          titles, build a watchlist, and use the social features. You're responsible
          for keeping your login credentials secure and for what happens under your
          account.
        </p>

        <h2>Acceptable use</h2>
        <ul>
          <li>Don't impersonate another person or misrepresent your affiliation with
            anyone.</li>
          <li>Don't harass, threaten, or abuse other users. If someone does this to
            you, you can block them and file a report from their profile.</li>
          <li>Don't attempt to access another user's account or data you're not
            authorized to see.</li>
        </ul>

        <h2>Content you submit</h2>
        <p>
          Reviews, ratings, and watchlist names you write are yours. By marking a
          watchlist public or writing a review, you're choosing to make that specific
          content visible to other users of the app.
        </p>

        <h2>Termination</h2>
        <p>
          You can delete your account at any time from Settings. We may suspend or
          remove an account that violates the acceptable-use terms above, including
          in response to a user report.
        </p>

        <h2>No warranty</h2>
        <p>
          Re:Watch is provided as-is. Recommendations and match scores are generated
          from your rating history and are not guarantees you'll enjoy a given title.
        </p>

        <h2>Contact</h2>
        <p>
          Questions about these terms: [contact email]. These terms are governed by
          the laws of [jurisdiction].
        </p>
      </div>
    </div>
  );
}
