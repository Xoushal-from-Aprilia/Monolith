// The pictures of the app's intro film, redrawn for the web: same shapes, same timing.
// p goes from 0 to 1 in the 4.3 s after a scene appears, clock loops every 6 s.

export const SCENE_MS = 4300;
const TAU = Math.PI * 2;
const clamp01 = (x) => Math.max(0, Math.min(1, x));
const ease = (x) => 1 - Math.pow(1 - clamp01(x), 3);
export const stage = (p, a, b) => ease((p - a) / (b - a));

function seeded(seed) {
  let s = seed;
  return () => (s = (s * 16807) % 2147483647) / 2147483647;
}

function rgba(c, a = 1) { return `rgba(${c[0]},${c[1]},${c[2]},${Math.max(0, Math.min(1, a))})`; }

function rrect(ctx, x, y, w, h, r) {
  w = Math.max(0, w); h = Math.max(0, h);
  r = Math.max(0, Math.min(r, w / 2, h / 2));
  ctx.beginPath();
  ctx.moveTo(x + r, y);
  ctx.arcTo(x + w, y, x + w, y + h, r);
  ctx.arcTo(x + w, y + h, x, y + h, r);
  ctx.arcTo(x, y + h, x, y, r);
  ctx.arcTo(x, y, x + w, y, r);
  ctx.closePath();
}

function circle(ctx, x, y, r) { ctx.beginPath(); ctx.arc(x, y, Math.max(0, r), 0, TAU); }

/** points along lines and curves, to draw a path up to a fraction of its length */
function samplePath(segs) {
  const pts = [];
  let cur = null;
  for (const s of segs) {
    if (s[0] === "M") { cur = [s[1], s[2]]; pts.push(cur); }
    else if (s[0] === "L") { cur = [s[1], s[2]]; pts.push(cur); }
    else if (s[0] === "Q") {
      const [x0, y0] = cur;
      for (let i = 1; i <= 24; i++) {
        const t = i / 24, u = 1 - t;
        pts.push([u * u * x0 + 2 * u * t * s[1] + t * t * s[3], u * u * y0 + 2 * u * t * s[2] + t * t * s[4]]);
      }
      cur = [s[3], s[4]];
    } else if (s[0] === "C") {
      const [x0, y0] = cur;
      for (let i = 1; i <= 40; i++) {
        const t = i / 40, u = 1 - t;
        pts.push([
          u * u * u * x0 + 3 * u * u * t * s[1] + 3 * u * t * t * s[3] + t * t * t * s[5],
          u * u * u * y0 + 3 * u * u * t * s[2] + 3 * u * t * t * s[4] + t * t * t * s[6],
        ]);
      }
      cur = [s[5], s[6]];
    } else if (s[0] === "Z") { pts.push(pts[0]); }
  }
  return pts;
}

/** draws the first fraction f of a sampled path; returns the point where it stops */
function partial(ctx, pts, f, color, width) {
  let total = 0;
  const lens = [0];
  for (let i = 1; i < pts.length; i++) { total += Math.hypot(pts[i][0] - pts[i - 1][0], pts[i][1] - pts[i - 1][1]); lens.push(total); }
  const target = total * clamp01(f);
  ctx.beginPath();
  ctx.moveTo(pts[0][0], pts[0][1]);
  let end = pts[0];
  for (let i = 1; i < pts.length; i++) {
    if (lens[i] <= target) { ctx.lineTo(pts[i][0], pts[i][1]); end = pts[i]; continue; }
    const seg = lens[i] - lens[i - 1];
    const k = seg > 0 ? (target - lens[i - 1]) / seg : 0;
    end = [pts[i - 1][0] + (pts[i][0] - pts[i - 1][0]) * k, pts[i - 1][1] + (pts[i][1] - pts[i - 1][1]) * k];
    ctx.lineTo(end[0], end[1]);
    break;
  }
  ctx.strokeStyle = color; ctx.lineWidth = width; ctx.lineCap = "round"; ctx.lineJoin = "round";
  if (f > 0) ctx.stroke();
  return end;
}

function monolith(ctx, cx, baseY, w, h, ink, alpha, rise = 1) {
  const hh = h * rise, d = w * 0.26;
  ctx.fillStyle = rgba(ink, alpha * 0.55);
  ctx.beginPath(); ctx.moveTo(cx - w / 2, baseY - hh); ctx.lineTo(cx - w / 2 + d, baseY - hh - d * 0.8); ctx.lineTo(cx + w / 2 + d, baseY - hh - d * 0.8); ctx.lineTo(cx + w / 2, baseY - hh); ctx.closePath(); ctx.fill();
  ctx.fillStyle = rgba(ink, alpha * 0.3);
  ctx.beginPath(); ctx.moveTo(cx + w / 2, baseY - hh); ctx.lineTo(cx + w / 2 + d, baseY - hh - d * 0.8); ctx.lineTo(cx + w / 2 + d, baseY - d * 0.8); ctx.lineTo(cx + w / 2, baseY); ctx.closePath(); ctx.fill();
  ctx.fillStyle = rgba(ink, alpha);
  ctx.fillRect(cx - w / 2, baseY - hh, w, hh);
}

function line(ctx, x1, y1, x2, y2, color, width) {
  ctx.strokeStyle = color; ctx.lineWidth = width; ctx.lineCap = "round";
  ctx.beginPath(); ctx.moveTo(x1, y1); ctx.lineTo(x2, y2); ctx.stroke();
}

function rotated(ctx, deg, px, py, fn) {
  ctx.save(); ctx.translate(px, py); ctx.rotate((deg * Math.PI) / 180); ctx.translate(-px, -py); fn(); ctx.restore();
}

/** one picture of the film, drawn in a w×h box; ink and bg are [r,g,b] */
export function drawArt(ctx, art, w, h, ink, bg, p, clock) {
  // the app draws on a ~975 px wide canvas: scale its pixel sizes to this one
  const u = (v) => (v * w) / 975;
  const c = { x: w / 2, y: h / 2 };
  const m = Math.min(w, h);
  ctx.clearRect(0, 0, w, h);
  switch (art) {
    case "intro":
    case "end": {
      for (let k = 0; k < 4; k++) {
        const f = (clock * 2 + k / 4) % 1;
        circle(ctx, c.x, h * 0.78, m * (0.15 + f * 0.5));
        ctx.strokeStyle = rgba(ink, 0.14 * (1 - f)); ctx.lineWidth = u(2.5); ctx.stroke();
      }
      if (art === "end") {
        const r = seeded(3);
        for (let i = 0; i < 60; i++) {
          const tw = 0.5 + 0.5 * Math.sin(clock * TAU * 2 + i);
          const x = r() * w, y = r() * h * 0.6, rad = u(1.5 + r() * 2);
          circle(ctx, x, y, rad); ctx.fillStyle = rgba(ink, 0.5 * tw); ctx.fill();
        }
      }
      ctx.fillStyle = rgba(ink, 0.3); ctx.fillRect(w * 0.12, h * 0.78, w * 0.76, Math.max(1, u(2.5)));
      monolith(ctx, c.x, h * 0.78, w * 0.2, h * 0.55, ink, 1, stage(p, 0, 0.35));
      break;
    }
    case "agenda": {
      const cols = 7, rows = 5, gw = w * 0.9, cell = gw / cols, ox = (w - gw) / 2;
      const oy = Math.max(h * 0.12, (h * 0.78 - rows * cell) / 2);
      const events = new Set([4, 11, 16, 23, 30]);
      for (let r = 0; r < rows; r++) for (let k = 0; k < cols; k++) {
        const i = r * cols + k;
        const s = stage(p, i * 0.012, i * 0.012 + 0.2);
        const x = ox + k * cell + cell * 0.08, y = oy + r * cell + cell * 0.08;
        const sz = cell * 0.84 * s, ev = events.has(i);
        rrect(ctx, x + (cell * 0.84 - sz) / 2, y + (cell * 0.84 - sz) / 2, sz, sz, cell * 0.18);
        ctx.fillStyle = rgba(ink, ev ? 0.9 : 0.12); ctx.fill();
        if (ev && s > 0.9) { circle(ctx, x + cell * 0.42, y + cell * 0.42, cell * 0.08); ctx.fillStyle = rgba(bg); ctx.fill(); }
      }
      const n = Math.max(3, 12 - Math.floor(stage(p, 0.4, 0.9) * 9));
      const pill = stage(p, 0.35, 0.5);
      const py = Math.min(h * 0.82, oy + rows * cell + h * 0.06);
      rrect(ctx, w * 0.55, py, w * 0.4 * pill, h * 0.13, h * 0.065); ctx.fillStyle = rgba(ink); ctx.fill();
      if (pill > 0.9) for (let d = 0; d < Math.min(n, 12); d++) { circle(ctx, w * 0.6 + d * (w * 0.3 / 12), py + h * 0.065, u(3.5)); ctx.fillStyle = rgba(bg); ctx.fill(); }
      break;
    }
    case "chat": {
      const items = [[true, 0.55], [false, 0.7], [true, 0.4], [false, 0.5]];
      items.forEach(([left, len], k) => {
        const s = stage(p, k * 0.12, k * 0.12 + 0.25);
        const bw = w * len, x = left ? w * 0.04 : w * 0.96 - bw, y = h * 0.06 + k * h * 0.15;
        rrect(ctx, x + (left ? -1 : 1) * (1 - s) * u(80), y, bw, h * 0.11, h * 0.055);
        ctx.fillStyle = rgba(ink, (left ? 0.18 : 0.9) * s); ctx.fill();
      });
      const g = Math.min(w * 0.36, h * 0.3), gx = c.x - g / 2, gy = h * 0.66;
      const lines = [
        [gx + g / 3, gy, gx + g / 3, gy + g * 0.9], [gx + 2 * g / 3, gy, gx + 2 * g / 3, gy + g * 0.9],
        [gx, gy + g * 0.3, gx + g, gy + g * 0.3], [gx, gy + g * 0.6, gx + g, gy + g * 0.6],
      ];
      lines.forEach(([ax, ay, bx, by], k) => {
        const s = stage(p, 0.45 + k * 0.04, 0.6 + k * 0.04);
        if (s > 0) line(ctx, ax, ay, ax + (bx - ax) * s, ay + (by - ay) * s, rgba(ink, 0.6), u(4));
      });
      const xo = stage(p, 0.7, 0.85);
      if (xo > 0) { circle(ctx, gx + g / 6, gy + g * 0.15, g * 0.1 * xo); ctx.strokeStyle = rgba(ink); ctx.lineWidth = u(5); ctx.stroke(); }
      const xx = stage(p, 0.8, 0.95);
      if (xx > 0) {
        const cx2 = gx + g / 2, cy2 = gy + g * 0.45, r = g * 0.1;
        line(ctx, cx2 - r, cy2 - r, cx2 - r + 2 * r * xx, cy2 - r + 2 * r * xx, rgba(ink), u(5));
      }
      break;
    }
    case "stories": {
      const rr = m * 0.13;
      for (let k = 0; k < 3; k++) {
        const s = stage(p, k * 0.1, k * 0.1 + 0.3);
        const cx = w * (0.22 + k * 0.28), cy = h * 0.22;
        ctx.beginPath(); ctx.arc(cx, cy, rr, -Math.PI / 2, -Math.PI / 2 + TAU * s);
        ctx.strokeStyle = rgba(ink); ctx.lineWidth = u(5); ctx.lineCap = "round"; if (s > 0) ctx.stroke();
        circle(ctx, cx, cy, rr * 0.78); ctx.fillStyle = rgba(ink, 0.15); ctx.fill();
      }
      const vr = m * 0.22, vx = c.x, vy = h * 0.68, vin = stage(p, 0.35, 0.6);
      rotated(ctx, clock * 360 * 2, vx, vy, () => {
        circle(ctx, vx, vy, vr * vin); ctx.fillStyle = rgba(ink); ctx.fill();
        for (let k = 1; k <= 5; k++) { circle(ctx, vx, vy, vr * vin * (0.45 + k * 0.1)); ctx.strokeStyle = rgba(bg, 0.18); ctx.lineWidth = u(1.5); ctx.stroke(); }
        circle(ctx, vx, vy, vr * vin * 0.28); ctx.fillStyle = rgba(bg); ctx.fill();
        line(ctx, vx, vy, vx + vr * vin * 0.9, vy, rgba(bg, 0.6), u(2));
      });
      for (let k = 0; k < 5; k++) {
        const bh = (0.3 + 0.7 * Math.abs(Math.sin(clock * TAU * 3 + k))) * h * 0.1 * vin;
        rrect(ctx, w * 0.78 + k * u(10), h * 0.9 - bh, u(6), bh, u(3)); ctx.fillStyle = rgba(ink); ctx.fill();
      }
      break;
    }
    case "calls": {
      const people = [[w * 0.3, h * 0.35], [w * 0.7, h * 0.35], [w * 0.3, h * 0.7], [w * 0.7, h * 0.7]];
      const rad = Math.min(w * 0.09, h * 0.09);
      people.forEach(([x, y], k) => {
        const s = stage(p, k * 0.1, k * 0.1 + 0.25);
        if (k === Math.floor(clock * 4) % 4) for (let q = 0; q < 2; q++) {
          const f = (clock * 8 + q / 2) % 1;
          circle(ctx, x, y, rad * 1.1 + f * rad * 0.9); ctx.strokeStyle = rgba(ink, 0.3 * (1 - f)); ctx.lineWidth = u(3); ctx.stroke();
        }
        circle(ctx, x, y, rad * s); ctx.fillStyle = rgba(ink); ctx.fill();
      });
      const sh = stage(p, 0.55, 0.8);
      if (sh > 0) { rrect(ctx, c.x - w * 0.14, h * 0.05, w * 0.28 * sh, h * 0.14, u(12)); ctx.strokeStyle = rgba(ink, 0.9); ctx.lineWidth = u(4); ctx.stroke(); }
      break;
    }
    case "map": {
      for (let k = 0; k < 6; k++) {
        line(ctx, 0, h * k / 5, w, h * k / 5 + h * 0.1, rgba(ink, 0.1), u(2));
        line(ctx, w * k / 5, 0, w * k / 5 - w * 0.08, h, rgba(ink, 0.1), u(2));
      }
      const px = w * 0.62, py = h * 0.45, pr = stage(p, 0, 0.3), R = Math.min(w * 0.2, h * 0.16);
      circle(ctx, px, py, R * pr); ctx.fillStyle = rgba(ink, 0.12); ctx.fill();
      ctx.strokeStyle = rgba(ink, 0.5); ctx.lineWidth = u(3); ctx.stroke();
      const route = samplePath([["M", w * 0.08, h * 0.9], ["C", w * 0.2, h * 0.5, w * 0.4, h * 0.8, px, py]]);
      const mv = stage(p, 0.2, 0.85);
      const end = partial(ctx, route, mv, rgba(ink), u(6));
      circle(ctx, end[0], end[1], u(14)); ctx.fillStyle = rgba(ink); ctx.fill();
      circle(ctx, end[0], end[1], u(6)); ctx.fillStyle = rgba(bg); ctx.fill();
      rotated(ctx, Math.sin(clock * TAU) * 25, w * 0.18, h * 0.18, () => {
        ctx.beginPath(); ctx.moveTo(w * 0.18, h * 0.08); ctx.lineTo(w * 0.23, h * 0.24); ctx.lineTo(w * 0.18, h * 0.2); ctx.lineTo(w * 0.13, h * 0.24); ctx.closePath();
        ctx.fillStyle = rgba(ink); ctx.fill();
      });
      break;
    }
    case "profile": {
      const cv = stage(p, 0, 0.3);
      rrect(ctx, 0, h * 0.05, w * cv, h * 0.34, u(26)); ctx.fillStyle = rgba(ink, 0.22); ctx.fill();
      for (let k = 0; k < 5; k++) {
        const y = h * 0.1 + k * h * 0.06;
        line(ctx, w * 0.05, y + ((clock * 40 + k * 13) % 30) * (h / 1400), w * 0.95 * cv, y, rgba(ink, 0.18 * cv), u(3));
      }
      const av = stage(p, 0.25, 0.45), ar = Math.min(w * 0.15, h * 0.12);
      circle(ctx, c.x, h * 0.4, ar * av); ctx.fillStyle = rgba(bg); ctx.fill();
      circle(ctx, c.x, h * 0.4, ar * 0.87 * av); ctx.strokeStyle = rgba(ink); ctx.lineWidth = u(6); if (av > 0) ctx.stroke();
      circle(ctx, c.x, h * 0.37, ar * 0.3 * av); ctx.fillStyle = rgba(ink); ctx.fill();
      for (let k = 0; k < 4; k++) {
        const s = stage(p, 0.45 + k * 0.08, 0.62 + k * 0.08);
        const bx = w * (0.2 + k * 0.2), by = h * 0.78, r = Math.min(w * 0.07, h * 0.06) * s;
        ctx.beginPath();
        for (let q = 0; q < 6; q++) {
          const a = q / 6 * TAU - TAU / 4;
          const x = bx + Math.cos(a) * r, y = by + Math.sin(a) * r;
          if (q === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y);
        }
        ctx.closePath();
        if (k % 2 === 0) { ctx.fillStyle = rgba(ink); ctx.fill(); } else { ctx.strokeStyle = rgba(ink); ctx.lineWidth = u(5); if (s > 0) ctx.stroke(); }
      }
      break;
    }
    case "bond": {
      [0.18, 0.3, 0.44, 0.6, 0.8].forEach((f, k) => {
        const s = stage(p, k * 0.12, k * 0.12 + 0.3);
        monolith(ctx, w * (0.12 + k * 0.18), h * 0.92, w * 0.1, h * f, ink, 0.35 + k * 0.16, s);
      });
      const fl = stage(p, 0.7, 0.9);
      circle(ctx, w * 0.86, h * 0.06 + u(30), u(20) * fl); ctx.fillStyle = rgba(ink); ctx.fill();
      circle(ctx, w * 0.86, h * 0.06 + u(30), u(8) * fl); ctx.fillStyle = rgba(bg); ctx.fill();
      break;
    }
    case "shard": {
      const railW = w * 0.2;
      rrect(ctx, 0, 0, railW, h, u(24)); ctx.fillStyle = rgba(ink, 0.1); ctx.fill();
      for (let k = 0; k < 4; k++) {
        const s = stage(p, k * 0.1, k * 0.1 + 0.25);
        const cy = h * 0.12 + k * h * 0.2, cx = railW / 2, r = railW * 0.32 * s;
        ctx.beginPath(); ctx.moveTo(cx, cy - r * 1.2); ctx.lineTo(cx + r, cy - r * 0.2); ctx.lineTo(cx - r * 0.1, cy + r * 1.2); ctx.lineTo(cx - r, cy); ctx.closePath();
        ctx.fillStyle = rgba(ink, k === 1 ? 1 : 0.5); ctx.fill();
      }
      for (let k = 0; k < 5; k++) {
        const s = stage(p, 0.3 + k * 0.08, 0.5 + k * 0.08);
        const y = h * 0.1 + k * h * 0.17;
        circle(ctx, railW + w * 0.1, y + u(10), u(6) * s); ctx.fillStyle = rgba(ink, 0.6); ctx.fill();
        rrect(ctx, railW + w * 0.16, y, (w * 0.55 - k * u(18)) * s, u(20), u(10)); ctx.fillStyle = rgba(ink, k < 3 ? 0.8 : 0.35); ctx.fill();
      }
      break;
    }
    case "together": {
      for (let k = 0; k < 6; k++) {
        const s = stage(p, k * 0.07, k * 0.07 + 0.35);
        const a = k / 6 * TAU + clock * TAU * 0.5;
        const fx = c.x + Math.cos(a) * w * 0.45, fy = c.y + Math.sin(a) * h * 0.4;
        circle(ctx, fx + (c.x - fx) * s * 0.7, fy + (c.y - fy) * s * 0.7, u(16));
        ctx.strokeStyle = rgba(ink, 0.8); ctx.lineWidth = u(4); ctx.stroke();
      }
      const side = Math.min(w * 0.28, h * 0.28);
      for (let k = 0; k < 3; k++) {
        const s = stage(p, 0.4 + k * 0.12, 0.6 + k * 0.12);
        rotated(ctx, (k - 1) * 12 * s, c.x, c.y, () => {
          rrect(ctx, c.x - side / 2, c.y - side * 0.55, side, side * 1.1, u(14));
          ctx.strokeStyle = rgba(ink, s); ctx.lineWidth = u(5); ctx.stroke();
        });
      }
      break;
    }
    case "safe": {
      const sw = Math.min(w, h * 0.8);
      const shield = samplePath([
        ["M", c.x, h * 0.08], ["L", c.x + sw * 0.3, h * 0.2], ["L", c.x + sw * 0.26, h * 0.6],
        ["Q", c.x + sw * 0.15, h * 0.82, c.x, h * 0.92], ["Q", c.x - sw * 0.15, h * 0.82, c.x - sw * 0.26, h * 0.6],
        ["L", c.x - sw * 0.3, h * 0.2], ["Z"],
      ]);
      partial(ctx, shield, stage(p, 0, 0.55), rgba(ink), u(7));
      const ck = stage(p, 0.55, 0.8);
      const check = samplePath([["M", c.x - sw * 0.1, h * 0.5], ["L", c.x - sw * 0.02, h * 0.6], ["L", c.x + sw * 0.13, h * 0.38]]);
      partial(ctx, check, ck, rgba(ink), u(10));
      for (let q = 0; q < 2; q++) {
        const f = (clock * 3 + q / 2) % 1;
        circle(ctx, c.x, c.y, sw * 0.35 + f * sw * 0.2); ctx.strokeStyle = rgba(ink, 0.15 * (1 - f) * ck); ctx.lineWidth = u(3); ctx.stroke();
      }
      break;
    }
    case "phone": {
      const hit = stage(p, 0.3, 0.5);
      const pw = Math.min(w * 0.18, h * 0.22), ph = pw * 2.2;
      const lx = w * 0.15 + hit * w * 0.22, rx = w * 0.85 - hit * w * 0.22;
      rotated(ctx, -15 + hit * 15, lx, c.y, () => { rrect(ctx, lx - pw / 2, c.y - ph / 2, pw, ph, u(20)); ctx.strokeStyle = rgba(ink); ctx.lineWidth = u(6); ctx.stroke(); });
      rotated(ctx, 15 - hit * 15, rx, c.y, () => { rrect(ctx, rx - pw / 2, c.y - ph / 2, pw, ph, u(20)); ctx.strokeStyle = rgba(ink); ctx.lineWidth = u(6); ctx.stroke(); });
      const burst = stage(p, 0.5, 0.75);
      if (burst > 0) for (let k = 0; k < 10; k++) {
        const a = k / 10 * TAU, r1 = w * 0.08 + burst * w * 0.05, r2 = r1 + w * 0.06 * (1 - burst);
        line(ctx, c.x + Math.cos(a) * r1, c.y + Math.sin(a) * r1, c.x + Math.cos(a) * r2, c.y + Math.sin(a) * r2, rgba(ink, 1 - burst * 0.6), u(5));
      }
      break;
    }
    case "install": {
      // the APK falls into the phone, fills a bar, then a check
      const pw = Math.min(w * 0.36, h * 0.4), ph = pw * 1.9, px = c.x - pw / 2, py = c.y - ph / 2;
      rrect(ctx, px, py, pw, ph, u(40)); ctx.strokeStyle = rgba(ink); ctx.lineWidth = u(6); ctx.stroke();
      line(ctx, c.x - pw * 0.12, py + u(22), c.x + pw * 0.12, py + u(22), rgba(ink, 0.5), u(5));
      const fall = stage(p, 0.05, 0.4), gone = stage(p, 0.36, 0.46);
      const ay = py - ph * 0.25 + fall * ph * 0.55;
      if (gone < 1) {
        const col = rgba(ink, 1 - gone);
        line(ctx, c.x, ay - pw * 0.22, c.x, ay + pw * 0.12, col, u(9));
        line(ctx, c.x - pw * 0.13, ay, c.x, ay + pw * 0.13, col, u(9));
        line(ctx, c.x + pw * 0.13, ay, c.x, ay + pw * 0.13, col, u(9));
      }
      const fill = stage(p, 0.42, 0.75);
      rrect(ctx, c.x - pw * 0.3, py + ph * 0.66, pw * 0.6, u(14), u(7)); ctx.fillStyle = rgba(ink, 0.15); ctx.fill();
      rrect(ctx, c.x - pw * 0.3, py + ph * 0.66, pw * 0.6 * fill, u(14), u(7)); ctx.fillStyle = rgba(ink); ctx.fill();
      const ck = stage(p, 0.75, 0.95);
      partial(ctx, samplePath([["M", c.x - pw * 0.18, py + ph * 0.42], ["L", c.x - pw * 0.04, py + ph * 0.5], ["L", c.x + pw * 0.2, py + ph * 0.3]]), ck, rgba(ink), u(11));
      for (let q = 0; q < 2; q++) {
        const f = (clock * 3 + q / 2) % 1;
        rrect(ctx, px - f * pw * 0.25, py - f * pw * 0.25, pw + f * pw * 0.5, ph + f * pw * 0.5, u(40) + f * pw * 0.25);
        ctx.strokeStyle = rgba(ink, 0.14 * (1 - f) * ck); ctx.lineWidth = u(3); ctx.stroke();
      }
      break;
    }
    case "update": {
      // the backup leaves the old monolith, the old one sinks, the new one rises and takes it
      const base = h * 0.84;
      ctx.fillStyle = rgba(ink, 0.3); ctx.fillRect(w * 0.06, base, w * 0.88, Math.max(1, u(2.5)));
      const sink = 1 - stage(p, 0.45, 0.75), rise = stage(p, 0.55, 0.9);
      monolith(ctx, w * 0.28, base, w * 0.16, h * 0.5, ink, 0.35, Math.max(0.02, sink));
      monolith(ctx, w * 0.7, base, w * 0.16, h * 0.56, ink, 1, Math.max(0.02, rise));
      const t = stage(p, 0.08, 0.5);
      const fx = w * 0.28 + (w * 0.7 - w * 0.28) * t, fy = h * 0.22 - Math.sin(t * Math.PI) * h * 0.12;
      const fw = Math.min(w * 0.12, h * 0.1), fh = fw * 1.25;
      ctx.save(); ctx.translate(fx - fw / 2, fy - fh / 2);
      ctx.beginPath(); ctx.moveTo(0, 0); ctx.lineTo(fw * 0.65, 0); ctx.lineTo(fw, fh * 0.28); ctx.lineTo(fw, fh); ctx.lineTo(0, fh); ctx.closePath();
      ctx.fillStyle = rgba(bg); ctx.fill(); ctx.strokeStyle = rgba(ink); ctx.lineWidth = u(5); ctx.lineJoin = "round"; ctx.stroke();
      ctx.beginPath(); ctx.moveTo(fw * 0.65, 0); ctx.lineTo(fw * 0.65, fh * 0.28); ctx.lineTo(fw, fh * 0.28); ctx.stroke();
      // a little lock: the backup is encrypted
      rrect(ctx, fw * 0.28, fh * 0.55, fw * 0.44, fh * 0.3, u(4)); ctx.fillStyle = rgba(ink); ctx.fill();
      ctx.beginPath(); ctx.arc(fw * 0.5, fh * 0.55, fw * 0.13, Math.PI, 0); ctx.lineWidth = u(4); ctx.stroke();
      ctx.restore();
      break;
    }
    default: { // wrapped
      const cw = Math.min(w * 0.36, h * 0.38);
      for (let k = 0; k < 4; k++) {
        const s = stage(p, k * 0.1, k * 0.1 + 0.35);
        rotated(ctx, (k - 1.5) * 9 * s, c.x, h * 0.95, () => {
          rrect(ctx, c.x - cw / 2, h * 0.12, cw, h * 0.7, u(22)); ctx.fillStyle = rgba(ink, (k % 2 === 0 ? 1 : 0.4) * s); ctx.fill();
        });
      }
      for (let k = 0; k < 7; k++) {
        const s = stage(p, 0.5 + k * 0.04, 0.75 + k * 0.04);
        const bh = h * (0.1 + ((k * 37) % 11) / 30) * s;
        rrect(ctx, c.x - cw * 0.39 + k * cw * 0.111, h * 0.7 - bh, cw * 0.07, bh, u(4)); ctx.fillStyle = rgba(bg); ctx.fill();
      }
    }
  }
}
