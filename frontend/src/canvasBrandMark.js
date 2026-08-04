// Canvas 2D redraw of BrandMark.jsx's aperture + decagon, for share cards
// rasterized via <canvas> (SVG/DOM styling doesn't carry into a flat PNG).
// Shared by every export-to-PNG flow (TasteProfile's card, Wrapped's card)
// so the two don't drift out of sync.
export function drawBrandMark(ctx, cx, cy, r) {
  ctx.strokeStyle = "#a855f7";
  ctx.lineWidth = 1.4;
  ctx.beginPath();
  ctx.arc(cx, cy, r, 0, Math.PI * 2);
  ctx.stroke();

  ctx.globalAlpha = 0.55;
  for (let i = 0; i < 6; i++) {
    const a = (i * 60 * Math.PI) / 180;
    ctx.beginPath();
    ctx.moveTo(cx + (r * 10) / 13 * Math.cos(a), cy + (r * 10) / 13 * Math.sin(a));
    ctx.lineTo(cx + r * Math.cos(a), cy + r * Math.sin(a));
    ctx.stroke();
  }

  ctx.globalAlpha = 0.85;
  ctx.fillStyle = "#a855f7";
  ctx.beginPath();
  for (let i = 0; i < 10; i++) {
    const a = -Math.PI / 2 + (i * 2 * Math.PI) / 10;
    const rr = i % 2 === 0 ? (r * 7) / 13 : (r * 4.8) / 13;
    const px = cx + rr * Math.cos(a);
    const py = cy + rr * Math.sin(a);
    if (i === 0) ctx.moveTo(px, py);
    else ctx.lineTo(px, py);
  }
  ctx.closePath();
  ctx.fill();
  ctx.globalAlpha = 1;
}
