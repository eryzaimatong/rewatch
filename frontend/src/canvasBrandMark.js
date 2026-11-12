// Canvas 2D redraw of the Re:Watch "replay" mark (assets/brand/rewatch-mark.svg:
// a swirl that doubles as a play button, ending in Ree's lavender spiral),
// for share cards rasterized via <canvas> — SVG/DOM styling doesn't carry into
// a flat PNG. Path data and gradient stops are copied verbatim from the
// brand file; only the transform is ours.
const MARK_W = 185.629;
const MARK_H = 166.541;

const BODY = new Path2D("M61 0C24 0 0 27 0 69C0 116 29 153 77 161C104 166 131 157 147 139C161 123 165 102 155 83C150 73 138 70 131 77C125 83 129 91 132 99C139 116 127 133 108 137C77 144 48 127 39 105C32 87 32 63 40 46C49 27 70 23 89 33C102 40 113 52 125 54C135 56 144 50 143 41C142 33 130 23 117 15C101 5 81 0 61 0ZM60 45C55 42 51 45 51 51V102C51 108 55 111 60 108L99 84C105 80 105 75 99 71L60 45Z");
const TAIL = new Path2D("M79 151C111 163 150 153 167 128C181 107 176 81 159 74C144 67 128 75 126 89C124 101 133 110 142 107C148 105 149 99 146 96");
const SHINE = new Path2D("M14 56C17 27 34 11 60 10C79 9 99 16 113 26");

function gradient(ctx, x1, y1, x2, y2, stops) {
  const g = ctx.createLinearGradient(x1, y1, x2, y2);
  stops.forEach(([o, c]) => g.addColorStop(o, c));
  return g;
}

/** Draws the mark centered on (cx, cy), about 2.4 × r wide. */
export function drawBrandMark(ctx, cx, cy, r) {
  const s = (r * 2.4) / MARK_W;
  ctx.save();
  ctx.translate(cx - (MARK_W * s) / 2, cy - (MARK_H * s) / 2);
  ctx.scale(s, s);

  ctx.fillStyle = gradient(ctx, 23, 13, 145, 154, [
    [0, "#8AE8DB"], [0.38, "#45C9BE"], [0.73, "#26A9AA"], [1, "#8B85C0"]
  ]);
  ctx.fill(BODY, "evenodd");

  ctx.lineCap = "round";
  ctx.lineWidth = 21;
  ctx.strokeStyle = gradient(ctx, 101, 135, 172, 81, [
    [0, "#479EAE"], [0.48, "#A69AD7"], [1, "#D1C3EC"]
  ]);
  ctx.stroke(TAIL);

  ctx.lineWidth = 3;
  ctx.strokeStyle = gradient(ctx, 22, 15, 96, 51, [
    [0, "rgba(193,245,231,0.7)"], [1, "rgba(138,232,219,0)"]
  ]);
  ctx.stroke(SHINE);
  ctx.restore();
}
