// Shared drawing kit of the Monolith motion videos (1080×1920 canvas, everything a function of time).
const W = 1080, H = 1920, FPS = 30;
const BK = [5, 5, 5], WH = [241, 241, 239], DK = [18, 18, 18];
const TAU = Math.PI * 2;
const c01 = (x) => Math.max(0, Math.min(1, x));
const seg = (t, a, b) => c01((t - a) / (b - a));
const lerp = (a, b, k) => a + (b - a) * k;
const rgba = (c, a = 1) => `rgba(${c[0]},${c[1]},${c[2]},${c01(a)})`;
const E = {
  outCubic: (x) => 1 - Math.pow(1 - c01(x), 3),
  inCubic: (x) => Math.pow(c01(x), 3),
  inOutCubic: (x) => { x = c01(x); return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2; },
  outExpo: (x) => { x = c01(x); return x === 1 ? 1 : 1 - Math.pow(2, -10 * x); },
  inExpo: (x) => { x = c01(x); return x === 0 ? 0 : Math.pow(2, 10 * x - 10); },
  outBack: (x, s = 1.70158) => { x = c01(x); const c3 = s + 1; return 1 + c3 * Math.pow(x - 1, 3) + s * Math.pow(x - 1, 2); },
  inBack: (x, s = 1.70158) => { x = c01(x); return (s + 1) * x * x * x - s * x * x; },
  outBounce: (x) => {
    x = c01(x); const n = 7.5625, d = 2.75;
    if (x < 1 / d) return n * x * x;
    if (x < 2 / d) return n * (x -= 1.5 / d) * x + 0.75;
    if (x < 2.5 / d) return n * (x -= 2.25 / d) * x + 0.9375;
    return n * (x -= 2.625 / d) * x + 0.984375;
  },
};
function seeded(seed) { let s = seed; return () => (s = (s * 16807) % 2147483647) / 2147483647; }

// ---------------------------------------------------------------- drawing helpers
function rr(ctx, x, y, w, h, r) { w = Math.max(0, w); h = Math.max(0, h); ctx.beginPath(); ctx.roundRect(x, y, w, h, Math.max(0, Math.min(r, w / 2, h / 2))); }
function circ(ctx, x, y, r) { ctx.beginPath(); ctx.arc(x, y, Math.max(0, r), 0, TAU); }
function font(ctx, size, weight = 900, ls = 0) { ctx.font = `${weight} ${size}px Inter`; ctx.letterSpacing = `${ls}px`; }
function txt(ctx, s, x, y, o) {
  ctx.save();
  font(ctx, o.size, o.weight || 900, o.ls || 0);
  ctx.textAlign = o.align || "center";
  ctx.textBaseline = "middle";
  ctx.fillStyle = rgba(o.color || WH, o.alpha ?? 1);
  // letter spacing is also added after the last letter: shift centred text back by half of it
  ctx.fillText(s, x + (o.align === "left" ? 0 : (o.ls || 0) / 2), y);
  ctx.restore();
}
function measure(ctx, s, size, weight = 900, ls = 0) { ctx.save(); font(ctx, size, weight, ls); const w = ctx.measureText(s).width; ctx.restore(); return w; }
function fitSize(ctx, s, size, max, weight = 900) { const w = measure(ctx, s, size, weight); return w > max ? (size * max) / w : size; }

/** a line of words that slide up out of a mask, one after the other */
function words(ctx, list, cx, y, size, t, t0, stagger, color, alpha = 1, weight = 900) {
  ctx.save();
  font(ctx, size, weight, -size * 0.02);
  ctx.textBaseline = "middle";
  ctx.textAlign = "left";
  const space = ctx.measureText(" ").width;
  const widths = list.map((w) => ctx.measureText(w).width);
  const total = widths.reduce((a, b) => a + b, 0) + space * (list.length - 1);
  let x = cx - total / 2;
  ctx.beginPath();
  ctx.rect(0, y - size * 0.8, W, size * 1.45);
  ctx.clip();
  list.forEach((w, k) => {
    const p = E.outExpo(seg(t, t0 + k * stagger, t0 + k * stagger + 0.55));
    if (p > 0) {
      ctx.fillStyle = rgba(color, alpha * Math.min(1, p * 1.6));
      ctx.fillText(w, x, y + (1 - p) * size * 1.2);
    }
    x += widths[k] + space;
  });
  ctx.restore();
}

/** the slab of the logo: front face, top and side */
function slab(ctx, cx, baseY, w, h, col, alpha = 1) {
  if (h <= 0) return;
  const d = w * 0.26;
  ctx.fillStyle = rgba(col, alpha * 0.55);
  ctx.beginPath(); ctx.moveTo(cx - w / 2, baseY - h); ctx.lineTo(cx - w / 2 + d, baseY - h - d * 0.8); ctx.lineTo(cx + w / 2 + d, baseY - h - d * 0.8); ctx.lineTo(cx + w / 2, baseY - h); ctx.closePath(); ctx.fill();
  ctx.fillStyle = rgba(col, alpha * 0.3);
  ctx.beginPath(); ctx.moveTo(cx + w / 2, baseY - h); ctx.lineTo(cx + w / 2 + d, baseY - h - d * 0.8); ctx.lineTo(cx + w / 2 + d, baseY - d * 0.8); ctx.lineTo(cx + w / 2, baseY); ctx.closePath(); ctx.fill();
  ctx.fillStyle = rgba(col, alpha);
  ctx.fillRect(cx - w / 2, baseY - h, w, h);
}

function samplePath(pts, steps = 30) {
  // pts: [[x,y], ...] with optional quadratic control as [x,y,cx,cy]
  const out = [pts[0].slice(0, 2)];
  for (let i = 1; i < pts.length; i++) {
    const [x, y, qx, qy] = pts[i], [x0, y0] = out[out.length - 1];
    if (qx === undefined) { out.push([x, y]); continue; }
    for (let s = 1; s <= steps; s++) { const k = s / steps, u = 1 - k; out.push([u * u * x0 + 2 * u * k * qx + k * k * x, u * u * y0 + 2 * u * k * qy + k * k * y]); }
  }
  return out;
}
function partial(ctx, pts, f, color, width) {
  let total = 0; const lens = [0];
  for (let i = 1; i < pts.length; i++) { total += Math.hypot(pts[i][0] - pts[i - 1][0], pts[i][1] - pts[i - 1][1]); lens.push(total); }
  const target = total * c01(f);
  let end = pts[0];
  ctx.beginPath(); ctx.moveTo(pts[0][0], pts[0][1]);
  for (let i = 1; i < pts.length; i++) {
    if (lens[i] <= target) { ctx.lineTo(pts[i][0], pts[i][1]); end = pts[i]; continue; }
    const k = (target - lens[i - 1]) / Math.max(1e-6, lens[i] - lens[i - 1]);
    end = [lerp(pts[i - 1][0], pts[i][0], k), lerp(pts[i - 1][1], pts[i][1], k)];
    ctx.lineTo(end[0], end[1]); break;
  }
  ctx.strokeStyle = color; ctx.lineWidth = width; ctx.lineCap = "round"; ctx.lineJoin = "round";
  if (f > 0) ctx.stroke();
  return end;
}
function hexPath(ctx, x, y, r) {
  ctx.beginPath();
  for (let q = 0; q < 6; q++) { const a = (q / 6) * TAU - TAU / 4; const px = x + Math.cos(a) * r, py = y + Math.sin(a) * r; q ? ctx.lineTo(px, py) : ctx.moveTo(px, py); }
  ctx.closePath();
}
function shardPath(ctx, cx, cy, r) {
  ctx.beginPath(); ctx.moveTo(cx, cy - r * 1.2); ctx.lineTo(cx + r, cy - r * 0.2); ctx.lineTo(cx - r * 0.1, cy + r * 1.2); ctx.lineTo(cx - r, cy); ctx.closePath();
}
function pinPath(ctx, x, tipY, r) {
  // a map pin: round head, point at the bottom
  const cy = tipY - r * 1.75;
  const a = Math.acos(1 / 1.75);           // where the sides touch the head
  ctx.beginPath();
  ctx.moveTo(x, tipY);
  ctx.arc(x, cy, r, Math.PI / 2 + a, Math.PI / 2 - a + TAU, false);
  ctx.closePath();
}

// ---------------------------------------------------------------- film grain
const grain = [];
{
  const rnd = seeded(7);
  for (let k = 0; k < 4; k++) {
    const g = document.createElement("canvas"); g.width = g.height = 256;
    const gc = g.getContext("2d"), img = gc.createImageData(256, 256);
    for (let i = 0; i < img.data.length; i += 4) { const v = Math.floor(rnd() * 255); img.data[i] = img.data[i + 1] = img.data[i + 2] = v; img.data[i + 3] = 255; }
    gc.putImageData(img, 0, 0);
    grain.push(g);
  }
}
function drawGrain(ctx, t) {
  const g = grain[Math.floor(t * FPS) % grain.length];
  ctx.save(); ctx.globalAlpha = 0.035;
  ctx.fillStyle = ctx.createPattern(g, "repeat"); ctx.fillRect(0, 0, W, H);
  ctx.restore();
}

// camera shake after the big hits
function shake(t, hits) {
  let x = 0, y = 0;
  for (const [t0, a] of hits) { const d = t - t0; if (d >= 0 && d < 0.6) { const k = a * Math.exp(-d * 8); x += k * Math.sin(d * 71); y += k * Math.cos(d * 57); } }
  return [x, y];
}
function bg(ctx, c) { ctx.fillStyle = rgba(c); ctx.fillRect(-60, -60, W + 120, H + 120); }

// ---------------------------------------------------------------- feature icons (drawn around 540, 545 on a light background)
function iconChat(ctx, u) {
  const list = [[270, 430, 330, true, 0.02], [810, 535, 270, false, 0.18], [270, 640, 210, true, 0.34]];
  list.forEach(([ax, y, w, left, t0], k) => {
    const s = E.outBack(seg(u, t0, t0 + 0.28), 2.4);
    if (s <= 0) return;
    const h = 84;
    ctx.save();
    ctx.translate(ax, y + h); ctx.scale(s, s); ctx.translate(-ax, -(y + h));
    rr(ctx, left ? ax : ax - w, y, w, h, 42);
    ctx.fillStyle = left ? rgba(BK, 0.12) : rgba(BK); ctx.fill();
    if (k === 2) for (let q = 0; q < 3; q++) {
      const b = Math.max(0, Math.sin(u * 16 - q * 0.9)) * 10;
      circ(ctx, ax + 62 + q * 38, y + h / 2 - b, 10); ctx.fillStyle = rgba(BK, 0.6); ctx.fill();
    }
    ctx.restore();
  });
}
function person(ctx, x, y, r, col) {
  ctx.save(); circ(ctx, x, y, r); ctx.clip();
  circ(ctx, x, y - r * 0.2, r * 0.3); ctx.fillStyle = rgba(col); ctx.fill();
  ctx.beginPath(); ctx.ellipse(x, y + r * 0.62, r * 0.55, r * 0.42, 0, Math.PI, TAU); ctx.fill();
  ctx.restore();
}
function iconCall(ctx, u) {
  const x = 540, y = 545, s = E.outBack(seg(u, 0, 0.25), 2.2);
  for (let q = 0; q < 3; q++) {
    const f = (u * 1.7 + q / 3) % 1;
    if (u < 0.1) continue;
    circ(ctx, x, y, 115 + f * 170); ctx.strokeStyle = rgba(BK, 0.45 * (1 - f)); ctx.lineWidth = 6; ctx.stroke();
  }
  circ(ctx, x, y, 108 * s); ctx.fillStyle = rgba(BK); ctx.fill();
  if (s > 0.2) person(ctx, x, y + 6, 100 * s, WH);
}
function iconStory(ctx, u) {
  const x = 540, y = 545, s = E.outBack(seg(u, 0, 0.25), 2);
  circ(ctx, x, y, 104 * s); ctx.fillStyle = rgba(BK, 0.1); ctx.fill();
  if (s > 0.3) {
    ctx.save(); ctx.translate(x, y); ctx.scale(s, s);
    circ(ctx, -30, -28, 17); ctx.fillStyle = rgba(BK, 0.55); ctx.fill();
    ctx.beginPath(); ctx.moveTo(-72, 50); ctx.lineTo(-18, -8); ctx.lineTo(20, 32); ctx.lineTo(42, 10); ctx.lineTo(78, 50); ctx.closePath(); ctx.fill();
    ctx.restore();
  }
  const p = E.outCubic(seg(u, 0.06, 0.6)) * TAU, gap = 0.22;
  ctx.strokeStyle = rgba(BK); ctx.lineWidth = 13; ctx.lineCap = "round";
  for (let k = 0; k < 3; k++) {
    const a0 = -Math.PI / 2 + (k * TAU) / 3 + gap / 2, a1 = a0 + TAU / 3 - gap;
    const end = Math.min(a1, -Math.PI / 2 + p);
    if (end > a0) { ctx.beginPath(); ctx.arc(x, y, 132, a0, end); ctx.stroke(); }
  }
}
function iconPin(ctx, u) {
  const fade = seg(u, 0, 0.15);
  ctx.save();
  ctx.beginPath(); ctx.rect(120, 560, 840, 220); ctx.clip();
  ctx.strokeStyle = rgba(BK, 0.13 * fade); ctx.lineWidth = 3;
  for (let k = 0; k < 6; k++) { const yy = 600 + Math.pow(k / 5, 1.6) * 170; ctx.beginPath(); ctx.moveTo(120, yy); ctx.lineTo(960, yy); ctx.stroke(); }
  for (let k = -5; k <= 5; k++) { ctx.beginPath(); ctx.moveTo(540 + k * 40, 560); ctx.lineTo(540 + k * 190, 780); ctx.stroke(); }
  ctx.restore();
  const route = samplePath([[190, 760], [540, 640, 300, 600]]);
  ctx.setLineDash([2, 22]);
  partial(ctx, route, E.outCubic(seg(u, 0.25, 0.7)), rgba(BK, 0.7), 10);
  ctx.setLineDash([]);
  const drop = E.outBounce(seg(u, 0.05, 0.6));
  const tip = lerp(250, 640, drop);
  ctx.beginPath(); ctx.ellipse(540, 642, 54 * drop, 13 * drop, 0, 0, TAU); ctx.fillStyle = rgba(BK, 0.18); ctx.fill();
  if (u > 0.05) {
    pinPath(ctx, 540, tip, 62); ctx.fillStyle = rgba(BK); ctx.fill();
    circ(ctx, 540, tip - 62 * 1.75, 24); ctx.fillStyle = rgba(WH); ctx.fill();
  }
}
function miniIcon(ctx, kind, x, y, s, col) {
  ctx.fillStyle = rgba(col); ctx.strokeStyle = rgba(col); ctx.lineWidth = s * 0.16;
  if (kind === 0) { rr(ctx, x - s, y - s * 0.45, s * 2, s * 0.9, s * 0.45); ctx.fill(); }
  else if (kind === 1) { pinPath(ctx, x, y + s, s * 0.55); ctx.fill(); }
  else if (kind === 2) { circ(ctx, x, y, s * 0.8); ctx.stroke(); }
  else if (kind === 3) { hexPath(ctx, x, y, s); ctx.fill(); }
  else { shardPath(ctx, x, y, s * 0.75); ctx.fill(); }
}
