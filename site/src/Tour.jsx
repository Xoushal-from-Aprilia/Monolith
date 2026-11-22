import { useEffect, useRef, useState } from "react";
import { AnimatePresence, motion, useReducedMotion } from "framer-motion";
import { Glyph } from "./Glyphs.jsx";

const TABS = [
  { id: "agenda", name: "Agenda", icon: Glyph.agenda, shot: "shots/agenda.webp",
    text: "La settimana a colpo d'occhio, eventi con promemoria, inviti agli amici. Dentro ci sono anche liste, spese, album e abitudini." },
  { id: "chat", name: "Chat", icon: Glyph.chat, shot: "shots/chat.webp",
    text: "Private e di gruppo, con vocali, storie, stati di 24 ore, giochi e sticker fatti con le tue foto." },
  { id: "map", name: "Mappa", icon: Glyph.map, shot: null,
    text: "Condividi la posizione quando vuoi tu, avvisa che stai arrivando, crea luoghi comuni che ti dicono quando un amico è arrivato." },
  { id: "friends", name: "Amici", icon: Glyph.friends, shot: "shots/amici.webp",
    text: "Aggiungili con il nome utente, con il codice QR o battendo il cinque con i telefoni vicini." },
  { id: "shard", name: "Shard", icon: Glyph.shard, shot: "shots/shard.webp",
    text: "Lo spazio del tuo gruppo, come su Discord: stanze scritte, vocali e private, bacheca, eventi e ruoli." },
];

/** the map has no screenshot: an illustration of people and a common place, drawn live */
function MapArt() {
  const ref = useRef(null);
  const still = useReducedMotion();
  useEffect(() => {
    const c = ref.current;
    const ctx = c.getContext("2d");
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    let raf = 0;
    const w = c.clientWidth, h = c.clientHeight;
    c.width = w * dpr; c.height = h * dpr; ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    // streets
    const rnd = (a) => { let s = a; return () => (s = (s * 9301 + 49297) % 233280) / 233280; };
    const r = rnd(7);
    const roads = Array.from({ length: 16 }, (_, i) => {
      const vert = i % 2 === 0;
      const p = r();
      return vert ? [p * w, -20, p * w + (r() - 0.5) * 120, h + 20] : [-20, p * h, w + 20, p * h + (r() - 0.5) * 120];
    });
    const people = [
      { x: 0.3, y: 0.32, e: "A", path: [[0.3, 0.32], [0.48, 0.5]] },
      { x: 0.72, y: 0.7, e: "L", path: [[0.72, 0.72], [0.56, 0.54]] },
      { x: 0.2, y: 0.78, e: "M", path: [[0.2, 0.78], [0.24, 0.74]] },
    ];
    const draw = (t) => {
      ctx.fillStyle = "#ececea"; ctx.fillRect(0, 0, w, h);
      ctx.strokeStyle = "#ffffff"; ctx.lineWidth = 9;
      roads.forEach(([a, b, x, y]) => { ctx.beginPath(); ctx.moveTo(a, b); ctx.lineTo(x, y); ctx.stroke(); });
      ctx.strokeStyle = "#d6d6d3"; ctx.lineWidth = 1;
      roads.forEach(([a, b, x, y]) => { ctx.beginPath(); ctx.moveTo(a, b); ctx.lineTo(x, y); ctx.stroke(); });
      // the common place
      const cx = 0.52 * w, cy = 0.52 * h;
      const pulse = still ? 0.5 : (t / 2200) % 1;
      ctx.strokeStyle = `rgba(10,10,11,${0.5 * (1 - pulse)})`; ctx.lineWidth = 2;
      ctx.beginPath(); ctx.arc(cx, cy, 30 + pulse * 40, 0, Math.PI * 2); ctx.stroke();
      ctx.fillStyle = "rgba(10,10,11,.08)"; ctx.beginPath(); ctx.arc(cx, cy, 30, 0, Math.PI * 2); ctx.fill();
      ctx.strokeStyle = "#0a0a0b"; ctx.setLineDash([4, 4]); ctx.beginPath(); ctx.arc(cx, cy, 30, 0, Math.PI * 2); ctx.stroke(); ctx.setLineDash([]);
      // friends walking towards it
      people.forEach((p, i) => {
        const k = still ? 0.6 : (Math.sin(t / 2600 + i * 2) + 1) / 2;
        const [a, b] = p.path;
        const x = (a[0] + (b[0] - a[0]) * k) * w, y = (a[1] + (b[1] - a[1]) * k) * h;
        ctx.fillStyle = "#ffffff"; ctx.beginPath(); ctx.arc(x, y, 17, 0, Math.PI * 2); ctx.fill();
        ctx.fillStyle = "#0a0a0b"; ctx.beginPath(); ctx.arc(x, y, 14, 0, Math.PI * 2); ctx.fill();
        ctx.fillStyle = "#f3f2ee"; ctx.font = "600 13px Inter, sans-serif"; ctx.textAlign = "center"; ctx.textBaseline = "middle";
        ctx.fillText(p.e, x, y + 1);
      });
      if (!still) raf = requestAnimationFrame(draw);
    };
    draw(0);
    return () => cancelAnimationFrame(raf);
  }, [still]);
  return (
    <div style={{ position: "absolute", inset: 0 }}>
      <canvas ref={ref} style={{ width: "100%", height: "100%", display: "block" }} aria-hidden="true" />
      <div style={{ position: "absolute", left: 14, right: 14, bottom: 18, background: "#0a0a0b", color: "#f3f2ee", borderRadius: 18, padding: "12px 14px", fontSize: 13, lineHeight: 1.35 }}>
        <b style={{ fontFamily: "var(--display)", fontSize: 15 }}>Leo sta arrivando</b>
        <div style={{ opacity: 0.7 }}>A 650 m · circa 8 minuti</div>
      </div>
      <div style={{ position: "absolute", top: 46, left: 14, background: "#fff", color: "#0a0a0b", borderRadius: 999, padding: "6px 12px", fontSize: 12, fontWeight: 600 }}>
        Illustrazione della mappa
      </div>
    </div>
  );
}

export function Phone({ src, alt, small, children }) {
  return (
    <div className={small ? "phone small" : "phone"}>
      <div className="cam" />
      <div className="screen">
        {children ?? <img src={src} alt={alt} loading="lazy" />}
      </div>
    </div>
  );
}

/** the five tabs of the app, with the phone showing each one; it moves on by itself until you pick one */
export function Tour() {
  const [i, setI] = useState(0);
  const [auto, setAuto] = useState(true);
  const still = useReducedMotion();
  const DUR = 5200;
  useEffect(() => {
    if (!auto || still) return;
    const id = setTimeout(() => setI((x) => (x + 1) % TABS.length), DUR);
    return () => clearTimeout(id);
  }, [i, auto, still]);
  const tab = TABS[i];
  return (
    <div className="tour">
      <div className="tabs" role="tablist" aria-label="Le schede di Monolith">
        {TABS.map((t, k) => (
          <button
            key={t.id} type="button" role="tab" id={`tab-${t.id}`} className="tab" aria-selected={k === i}
            onClick={() => { setI(k); setAuto(false); }}
          >
            <span className="ico"><t.icon /></span>
            <h3>{t.name}</h3>
            <p>{t.text}</p>
            {k === i && auto && !still && (
              <span className="bar"><motion.i key={`b${i}`} initial={{ scaleX: 0 }} animate={{ scaleX: 1 }} transition={{ duration: DUR / 1000, ease: "linear" }} /></span>
            )}
          </button>
        ))}
      </div>
      <Phone>
        <AnimatePresence initial={false} mode="popLayout">
          <motion.div
            key={tab.id}
            style={{ position: "absolute", inset: 0 }}
            initial={{ opacity: 0, scale: 1.04 }} animate={{ opacity: 1, scale: 1 }} exit={{ opacity: 0, scale: 0.98 }}
            transition={{ duration: 0.5, ease: [0.16, 1, 0.3, 1] }}
          >
            {tab.shot ? <img src={tab.shot} alt={`La scheda ${tab.name} di Monolith`} /> : <MapArt />}
          </motion.div>
        </AnimatePresence>
      </Phone>
    </div>
  );
}
