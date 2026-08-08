import { useState } from "react";
import { toggleReviewLike, getReviewComments, addReviewComment, deleteReviewComment, fileReport } from "./api";
import ReportDialog from "./ReportDialog";
import "./App.css";

/**
 * Like + comment thread for one review — a review being a Rating with a
 * non-blank moment. Shared between SocialProfile's Reviews section and
 * Community's Activity feed rather than duplicated, since both render the
 * same review-list shape (SocialService.reviews()/activityFeed(), enriched
 * with likeCount/commentCount/likedByCaller by ReviewService).
 */
export default function ReviewInteractions({ review }) {
  const [liked, setliked] = useState(!!review.likedByCaller);
  const [likeCount, setlikeCount] = useState(review.likeCount ?? 0);
  const [likeBusy, setlikeBusy] = useState(false);

  const [showComments, setshowComments] = useState(false);
  const [comments, setcomments] = useState([]);
  const [commentsLoaded, setcommentsLoaded] = useState(false);
  const [commentCount, setcommentCount] = useState(review.commentCount ?? 0);
  const [newComment, setnewComment] = useState("");
  const [newCommentSpoiler, setnewCommentSpoiler] = useState(false);
  const [posting, setposting] = useState(false);
  const [revealedSpoilers, setrevealedSpoilers] = useState(() => new Set());

  const [reportTarget, setreportTarget] = useState(null);
  const [reportSubmitting, setreportSubmitting] = useState(false);
  const [reportDoneId, setreportDoneId] = useState(null);

  const currentUserId = Number(localStorage.getItem("userId"));
  const currentUsername = localStorage.getItem("username") || "You";

  async function handlelike() {
    if (likeBusy) return;
    setlikeBusy(true);
    const res = await toggleReviewLike(review.ratingId);
    if (res?.status === "success") {
      setliked(res.liked);
      setlikeCount(res.likeCount);
    }
    setlikeBusy(false);
  }

  async function handletogglecomments() {
    const next = !showComments;
    setshowComments(next);
    if (next && !commentsLoaded) {
      setcomments(await getReviewComments(review.ratingId));
      setcommentsLoaded(true);
    }
  }

  async function handlepostcomment(e) {
    e.preventDefault();
    const body = newComment.trim();
    if (!body || posting) return;
    setposting(true);
    const res = await addReviewComment(review.ratingId, body, newCommentSpoiler);
    if (res?.status === "success") {
      setcomments((current) => [...current, {
        id: res.id,
        authorUserId: currentUserId,
        authorUsername: currentUsername,
        body: res.body,
        createdAt: res.createdAt,
        isOwn: true,
        hasSpoilers: !!res.hasSpoilers
      }]);
      setcommentCount((n) => n + 1);
      setnewComment("");
      setnewCommentSpoiler(false);
    }
    setposting(false);
  }

  function revealSpoiler(id) {
    setrevealedSpoilers((current) => new Set(current).add(id));
  }

  async function handledeletecomment(id) {
    setcomments((current) => current.filter((c) => c.id !== id));
    setcommentCount((n) => Math.max(0, n - 1));
    await deleteReviewComment(id);
  }

  async function submitcommentreport(reason, details) {
    setreportSubmitting(true);
    const res = await fileReport(reportTarget.authorUserId, reason, details, reportTarget.id);
    setreportSubmitting(false);
    if (res?.status === "success") {
      setreportDoneId(reportTarget.id);
      setreportTarget(null);
    }
  }

  return (
    <div className="review-interactions">
      <div className="review-interactions-row">
        <button
          type="button"
          className={`review-action-btn${liked ? " is-active" : ""}`}
          onClick={handlelike}
          disabled={likeBusy}
          aria-label={liked ? "Unlike this review" : "Like this review"}
        >
          {liked ? "♥" : "♡"} {likeCount > 0 ? likeCount : "Like"}
        </button>
        <button type="button" className="review-action-btn" onClick={handletogglecomments}>
          {commentCount > 0 ? `${commentCount} comment${commentCount === 1 ? "" : "s"}` : "Comment"}
        </button>
      </div>

      {showComments && (
        <div className="review-comment-thread">
          {comments.map((c) => (
            <div key={c.id} className="review-comment-row">
              <span className="review-comment-author">{c.authorUsername}</span>
              {c.hasSpoilers && !revealedSpoilers.has(c.id) ? (
                <button type="button" className="review-comment-spoiler-veil" onClick={() => revealSpoiler(c.id)}>
                  ⚠️ Contains spoilers — click to reveal
                </button>
              ) : (
                <span className="review-comment-body">{c.body}</span>
              )}
              {c.isOwn && (
                <button type="button" className="review-comment-delete" onClick={() => handledeletecomment(c.id)}>
                  Delete
                </button>
              )}
              {!c.isOwn && reportDoneId !== c.id && (
                <button type="button" className="review-comment-report" onClick={() => setreportTarget(c)}>
                  Report
                </button>
              )}
              {!c.isOwn && reportDoneId === c.id && (
                <span className="review-comment-reported">Reported</span>
              )}
            </div>
          ))}
          {comments.length === 0 && (
            <p className="review-comment-empty">No comments yet — be the first.</p>
          )}
          <form onSubmit={handlepostcomment} className="review-comment-form">
            <input
              type="text"
              placeholder="Add a comment..."
              value={newComment}
              onChange={(e) => setnewComment(e.target.value)}
              maxLength={500}
            />
            <button type="submit" className="pill" disabled={posting || !newComment.trim()}>
              {posting ? "..." : "Post"}
            </button>
          </form>
          {newComment.trim() && (
            <label className="review-comment-spoiler-check">
              <input
                type="checkbox"
                checked={newCommentSpoiler}
                onChange={(e) => setnewCommentSpoiler(e.target.checked)}
              />
              ⚠️ This contains spoilers
            </label>
          )}
        </div>
      )}

      {reportTarget && (
        <ReportDialog
          username={reportTarget.authorUsername}
          submitting={reportSubmitting}
          onSubmit={submitcommentreport}
          onCancel={() => setreportTarget(null)}
        />
      )}
    </div>
  );
}
