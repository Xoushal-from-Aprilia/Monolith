import { useEffect, useRef } from "react";
import { motion, useMotionValue, useSpring, useTransform, useReducedMotion, animate } from "framer-motion";
import qrcode from "qrcode-generator";
import { Glyph } from "./Glyphs.jsx";

/** a quiet sky: stars that twinkle and drift a little with the pointer; it stops when off screen */
export function Starfield({ px, py }) {
  const ref = useRef(null);
  const still = useReducedMotion();
  useEffect(() => {
    const c = ref.current;
    const ctx = c.getContext("2d");
    let w = 0, h = 0, raf = 0, visible = true;
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    const stars = Array.from({ length: 260 }, () => ({
      x: Math.random(), y: Math.random(), z: Math.random() * 0.9 + 0.1,
      r: Math.random() * 1.2 + 0.2, t: Math.random() * Math.PI * 2, s: Math.random() * 0.8 + 0.3,
    }));
    const size = () => {
      w = c.clientWidth; h = c.clientHeight;
      c.width = w * dpr; c.height = h * dpr;
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    };
    const draw = (time) => {
      ctx.clearRect(0, 0, w, h);
      const ox = (px.get() - 0.5) * 30, oy = (py.get() - 0.5) * 20;
      for (const s of stars) {
        const tw = still ? 0.7 : 0.45 + 0.55 * Math.abs(Math.sin(s.t + time * 0.0006 * s.s));
        ctx.globalAlpha = tw * (0.35 + s.z * 0.65);
        ctx.fillStyle = "#f3f2ee";
        ctx.beginPath();
        ctx.arc(s.x * w + ox * s.z, s.y * h + oy * s.z, s.r * (0.6 + s.z * 0.6), 0, Math.PI * 2);
        ctx.fill();
      }
      ctx.globalAlpha = 1;
      if (visible && !still) raf = requestAnimationFrame(draw);
    };
    size();
    draw(0);
    const io = new IntersectionObserver(([e]) => {
      visible = e.isIntersecting;
      cancelAnimationFrame(raf);
      if (visible && !still) raf = requestAnimationFrame(draw);
    });
    io.observe(c);
    const ro = new ResizeObserver(() => { size(); draw(performance.now()); });
    ro.observe(c);
    return () => { cancelAnimationFrame(raf); io.disconnect(); ro.disconnect(); };
  }, [px, py, still]);
  return <canvas className="stars" ref={ref} aria-hidden="true" />;
}

/** the monolith: a real 3D slab that turns slowly and leans towards the pointer */
export function Slab({ px, py }) {
  const still = useReducedMotion();
  const spin = useMotionValue(-24);
  const sx = useSpring(px, { stiffness: 60, damping: 18 });
  const sy = useSpring(py, { stiffness: 60, damping: 18 });
  const rotY = useTransform([spin, sx], ([s, x]) => s + (x - 0.5) * 50);
  const rotX = useTransform(sy, (y) => 8 - (y - 0.5) * 18);
  const sweep = useMotionValue(0);
  const sweepCss = useTransform(sweep, (v) => `${v}%`);
  useEffect(() => {
    if (still) return;
    const a = animate(spin, [-24, 24, -24], { duration: 18, repeat: Infinity, ease: "easeInOut" });
    const b = animate(sweep, [110, -10], { duration: 5.5, repeat: Infinity, repeatDelay: 2.5, ease: "easeInOut" });
    return () => { a.stop(); b.stop(); };
  }, [still, spin, sweep]);
  return (
    <div className="slab-stage" aria-hidden="true">
      <div className="slab-glow" />
      <div className="slab-ring" />
      <div className="slab-floor" />
      <motion.div
        className="slab"
        initial={{ y: 60, opacity: 0, scale: 0.92 }}
        animate={{ y: 0, opacity: 1, scale: 1 }}
        transition={{ duration: 1.4, ease: [0.16, 1, 0.3, 1], delay: 0.15 }}
        style={{ rotateY: rotY, rotateX: rotX, "--sweep": sweepCss }}
      >
        <div className="face front" />
        <div className="face back" />
        <div className="face left" />
        <div className="face right" />
        <div className="face top" />
        <div className="face bottom" />
      </motion.div>
    </div>
  );
}

/** a QR code of the download link, to grab the APK from the phone when the site is open on a computer */
export function Qr({ text }) {
  const q = qrcode(0, "M");
  q.addData(text);
  q.make();
  const n = q.getModuleCount();
  let d = "";
  for (let r = 0; r < n; r++) for (let c = 0; c < n; c++) if (q.isDark(r, c)) d += `M${c},${r}h1v1h-1z`;
  return (
    <svg viewBox={`0 0 ${n} ${n}`} shapeRendering="crispEdges" role="img" aria-label="Codice QR per scaricare l'ultima versione">
      <path d={d} fill="#070708" />
    </svg>
  );
}

export function Hero({ latest, onInstall, sizeText, dateText }) {
  const px = useMotionValue(0.5);
  const py = useMotionValue(0.5);
  const onMove = (e) => {
    const r = e.currentTarget.getBoundingClientRect();
    px.set((e.clientX - r.left) / r.width);
    py.set((e.clientY - r.top) / r.height);
  };
  const letters = "Monolith".split("");
  return (
    <header className="hero" onPointerMove={onMove}>
      <Starfield px={px} py={py} />
      <div className="wrap hero-grid">
        <div>
          <motion.span className="eyebrow" initial={{ y: 12 }} animate={{ y: 0 }} transition={{ duration: 0.8 }}>
            App per Android · gratis · senza pubblicità
          </motion.span>
          <h1 className="hero-title" aria-label="Monolith">
            {letters.map((l, i) => (
              <motion.span
                key={i} aria-hidden="true"
                initial={{ y: "70%" }} animate={{ y: "0%" }}
                transition={{ duration: 1, delay: 0.05 * i, ease: [0.16, 1, 0.3, 1] }}
              >{l}</motion.span>
            ))}
          </h1>
          <p className="hero-claim">Tutto quello che conta, tra amici.</p>
          <p className="hero-text">
            Chat, agenda, mappa, spese condivise e Shard per i tuoi gruppi, in un'unica app in bianco e nero.
            Niente email, niente numero di telefono, niente pubblicità: il tuo profilo vive sul tuo telefono.
          </p>
          <div className="hero-actions">
            <a className="btn btn-primary" href={latest.url} rel="noopener">
              <Glyph.download />
              Scarica la {latest.version}
            </a>
            <button className="btn btn-ghost" type="button" onClick={onInstall}>
              Come si installa
              <Glyph.arrow />
            </button>
          </div>
          <div className="hero-meta num">
            <span><b>APK</b> · {sizeText}</span>
            <span><b>Android 8.0</b> o più recente</span>
            <span>Aggiornata il {dateText}</span>
          </div>
        </div>
        <div className="hero-art">
          <Slab px={px} py={py} />
          <div className="qr-card">
            <Qr text={latest.url} />
            <p><b>Sei al computer?</b>Inquadra il codice con il telefono per scaricare l'APK lì.</p>
          </div>
        </div>
      </div>
    </header>
  );
}
