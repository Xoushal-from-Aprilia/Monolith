import { useEffect, useMemo, useRef, useState } from "react";
import qrcode from "qrcode-generator";
import versions from "./versions.json";
import { drawArt, SCENE_MS } from "./film.js";

// The site is the app's intro film, scrolled: one scene per screen, black and white in turn.
const DARK = { bg: [5, 5, 5], ink: [241, 241, 239] };
const LIGHT = { bg: [241, 241, 239], ink: [10, 10, 10] };
const REPO = "https://github.com/Xoushal-from-Aprilia/Monolith";
const MONTHS = ["gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno", "luglio", "agosto", "settembre", "ottobre", "novembre", "dicembre"];
const dateIt = (iso) => { const [y, m, d] = iso.split("-").map(Number); return `${d} ${MONTHS[m - 1]} ${y}`; };
const mb = (b) => (b / 1048576).toLocaleString("it-IT", { maximumFractionDigits: 1, minimumFractionDigits: 1 }) + " MB";
const latest = versions[0];

const SCENES = [
  { id: "inizio", art: "intro", kicker: "", title: "Monolith", lines: ["Tutto quello che conta, tra amici."], cta: "hero" },
  { id: "agenda", art: "agenda", kicker: "Agenda", title: "Il tuo tempo, condiviso", lines: ["Eventi e inviti agli amici", "Conto alla rovescia e meteo", "Compleanni e anniversari d'amicizia"] },
  { id: "chat", art: "chat", kicker: "Chat", title: "Chat che sanno giocare", lines: ["Giochi, dadi e lavagna condivisa", "Sticker, inoltri e foto a vista unica", "Date nei messaggi che diventano eventi"] },
  { id: "storie", art: "stories", kicker: "Storie", title: "Racconta la giornata", lines: ["Filtri in bianco e nero", "Musica nelle storie", "Risposte che arrivano in chat"] },
  { id: "chiamate", art: "calls", kicker: "Chiamate", title: "Voce, video e schermo", lines: ["Fino a 4 persone", "Cuffie Bluetooth e microfono a scelta", "Condividi lo schermo", "Ascolta musica insieme"] },
  { id: "mappa", art: "map", kicker: "Mappa", title: "Sai sempre dove trovarvi", lines: ["Posizione in diretta e arrivo in diretta", "Luoghi comuni e amici nei paraggi", "Bussola verso l'amico"] },
  { id: "profilo", art: "profile", kicker: "Profilo", title: "Il tuo spazio", lines: ["Copertine da scegliere", "Distintivi disegnati, tre in vetrina", "Tutto si modifica da un solo tasto"] },
  { id: "colori", art: "colors", kicker: "Colori", title: "Del colore che vuoi", lines: ["Dodici temi da scegliere", "Chiaro, scuro o dal tuo sfondo", "E l'icona che preferisci"] },
  { id: "legame", art: "bond", kicker: "Legame", title: "Più vi scrivete, più cresce", lines: ["Da Sasso a Monolite", "Serie di giorni insieme", "Il simbolo accanto al nome dopo 100 giorni"] },
  { id: "shard", art: "shard", kicker: "Shard", title: "Spazi per il tuo gruppo", lines: ["Stanze scritte, vocali e private", "Videocamera anche nelle stanze vocali", "Bacheca, eventi e ruoli", "Benvenuto e titoli per tutti"] },
  { id: "insieme", art: "together", kicker: "Insieme", title: "Spese, album, abitudini", lines: ["Chi deve quanto, in un tocco", "Le foto di tutti in un posto", "Abitudini da tenere insieme"] },
  { id: "sicurezza", art: "safe", kicker: "Sicurezza", title: "Al sicuro, sempre", lines: ["Tasto SOS con scheda medica", "Blocco e avviso screenshot", "Backup cifrato automatico"] },
  { id: "telefono", art: "phone", kicker: "Il tuo telefono", title: "Si muove con te", lines: ["Batti cinque per aggiungere amici", "Vocali all'orecchio", "Sfondo animato e widget", "Una piccola vibrazione a ogni tocco"] },
  { id: "domenica", art: "wrapped", kicker: "Domenica", title: "La tua settimana in slide", lines: ["Persone, emoji e ritmo", "Un titolo tutto tuo"] },
  { id: "scarica", art: "end", kicker: "", title: "Pronto?", lines: ["Il resto lo scopri usandola."], cta: "end" },
  {
    id: "installa", art: "install", kicker: "Installazione", title: "Quattro passi", steps: [
      "Scarica l'APK dal telefono",
      "Aprilo e consenti «Installa da questa fonte»",
      "Se Play Protect avvisa: Altri dettagli, poi Installa comunque",
      "Apri Monolith e crea il tuo profilo",
    ],
  },
  {
    id: "aggiornare", art: "update", kicker: "Aggiornare", title: "Dalla versione vecchia", steps: [
      "In Monolith: Impostazioni, Backup cifrato",
      "Disinstalla la versione vecchia",
      "Installa quella nuova",
      "Apri e scegli «Ho già un profilo Monolith»",
    ],
    note: "Per ora serve a ogni aggiornamento: ogni versione ha una firma diversa.",
  },
];
const N = SCENES.length;
const themeOf = (i) => (i % 2 === 0 ? DARK : LIGHT);
const css = (c) => `rgb(${c[0]},${c[1]},${c[2]})`;

// what the top bar needs, shared without re-rendering at every frame
const live = { index: 0, start: performance.now() };
const reduced = typeof matchMedia !== "undefined" && matchMedia("(prefers-reduced-motion: reduce)").matches;

function Qr({ text, ink }) {
  const q = qrcode(0, "M");
  q.addData(text);
  q.make();
  const n = q.getModuleCount();
  let d = "";
  for (let r = 0; r < n; r++) for (let c = 0; c < n; c++) if (q.isDark(r, c)) d += `M${c},${r}h1v1h-1z`;
  return (
    <svg className="qr" viewBox={`-2 -2 ${n + 4} ${n + 4}`} shapeRendering="crispEdges" role="img" aria-label="Codice QR per scaricare l'APK">
      <path d={d} fill={ink} />
    </svg>
  );
}

function Download({ big }) {
  return (
    <div className={big ? "cta cta-big" : "cta"}>
      <a className="btn" href={latest.url} rel="noopener">Scarica Monolith {latest.version}</a>
      <p className="meta">APK · {mb(latest.size)} · Android 8.0 o più recente</p>
    </div>
  );
}

function Scene({ s, index }) {
  const t = themeOf(index);
  const ref = useRef(null);
  const art = useRef(null);
  const canvas = useRef(null);
  const [on, setOn] = useState(index === 0);
  const state = useRef({ visible: index === 0, start: performance.now(), active: index === 0 });

  useEffect(() => {
    const cv = canvas.current;
    const ctx = cv.getContext("2d");
    let raf = 0, w = 0, h = 0;
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    const size = () => {
      const r = art.current.getBoundingClientRect();
      w = r.width; h = r.height;
      cv.width = Math.round(w * dpr); cv.height = Math.round(h * dpr);
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    };
    const frame = (now) => {
      const st = state.current;
      const p = reduced ? 1 : st.active ? Math.min(1, (now - st.start) / SCENE_MS) : 0;
      const clock = reduced ? 0.3 : (now % 6000) / 6000;
      if (w > 0 && h > 0) drawArt(ctx, s.art, w, h, t.ink, t.bg, p, clock);
      if (st.visible && !reduced) raf = requestAnimationFrame(frame);
    };
    size();
    frame(performance.now());
    const ro = new ResizeObserver(() => { size(); frame(performance.now()); });
    ro.observe(art.current);
    const io = new IntersectionObserver((entries) => {
      for (const e of entries) {
        const st = state.current;
        const wasVisible = st.visible;
        st.visible = e.isIntersecting;
        if (e.intersectionRatio >= 0.45 && !st.active) {
          st.active = true; st.start = performance.now(); setOn(true);
        } else if (e.intersectionRatio < 0.08 && st.active) {
          st.active = false; setOn(false);
        }
        if (st.visible && !wasVisible) { cancelAnimationFrame(raf); raf = requestAnimationFrame(frame); }
      }
    }, { threshold: [0, 0.08, 0.45, 0.7] });
    io.observe(ref.current);
    return () => { cancelAnimationFrame(raf); ro.disconnect(); io.disconnect(); };
  }, [s.art, t]);

  // the scene tells the bar where it is
  useEffect(() => {
    if (on) { live.index = index; live.start = state.current.start; }
  }, [on, index]);

  const style = { background: css(t.bg), color: css(t.ink), "--ink": css(t.ink), "--bg": css(t.bg) };
  const delay = (ms) => ({ "--d": `${ms}ms` });
  return (
    <section id={s.id} ref={ref} className={on || reduced ? "scene on" : "scene"} style={style} data-i={index}>
      <div className="art" ref={art} aria-hidden="true"><canvas ref={canvas} /></div>
      <div className="text">
        {s.kicker && <span className="kicker rv" style={delay(0)}>{s.kicker}</span>}
        <h2 className={s.art === "intro" ? "title title-hero rv" : "title rv"} style={delay(120)}>{s.title}</h2>
        {s.lines && (
          <ul className="lines">
            {s.lines.map((l, k) => <li key={l} className="rv" style={delay(380 + k * 260)}>{l}</li>)}
          </ul>
        )}
        {s.steps && (
          <ol className="lines steps">
            {s.steps.map((l, k) => <li key={l} className="rv" style={delay(380 + k * 260)}><b>{k + 1}</b>{l}</li>)}
          </ol>
        )}
        {s.note && <p className="note rv" style={delay(380 + s.steps.length * 260)}>{s.note}</p>}
        {s.cta && (
          <div className="rv" style={delay(s.cta === "hero" ? 640 : 700)}>
            <Download big={s.cta === "end"} />
            {s.cta === "end" && (
              <div className="qr-row">
                <Qr text={latest.url} ink={css(t.ink)} />
                <p>Sei al computer? Inquadra il codice con il telefono e l'APK si scarica lì.</p>
              </div>
            )}
          </div>
        )}
      </div>
    </section>
  );
}

/** the bar of the film: one segment per scene, the current one fills in 4.3 s */
function Bar() {
  const fills = useRef([]);
  const [theme, setTheme] = useState(DARK);
  useEffect(() => {
    let raf = 0, last = -2;
    const loop = (now) => {
      // which part of the page is under the bar
      const probe = window.innerHeight * 0.4;
      let idx = -1, extra = null;
      document.querySelectorAll("[data-i],[data-t]").forEach((el) => {
        const r = el.getBoundingClientRect();
        if (r.top <= probe && r.bottom > probe) { if (el.dataset.i !== undefined) idx = Number(el.dataset.i); else extra = el.dataset.t; }
      });
      const cur = extra ? N : idx;
      if (cur !== last) {
        last = cur;
        setTheme(extra ? (extra === "dark" ? DARK : LIGHT) : themeOf(Math.max(0, idx)));
      }
      fills.current.forEach((el, i) => {
        if (!el) return;
        let f = 0;
        if (cur >= N || i < cur) f = 1;
        else if (i === cur) f = reduced ? 1 : live.index === i ? Math.min(1, (now - live.start) / SCENE_MS) : 0;
        el.style.transform = `scaleX(${f})`;
      });
      raf = requestAnimationFrame(loop);
    };
    raf = requestAnimationFrame(loop);
    return () => cancelAnimationFrame(raf);
  }, []);
  const go = (e, id) => { e.preventDefault(); document.getElementById(id)?.scrollIntoView({ behavior: reduced ? "auto" : "smooth" }); };
  return (
    <header className="bar" style={{ background: css(theme.bg), color: css(theme.ink) }}>
      <div className="segs">
        {SCENES.map((s, i) => (
          <span key={s.id} className="seg"><i ref={(el) => (fills.current[i] = el)} /></span>
        ))}
      </div>
      <div className="bar-row">
        <a className="brand" href="#inizio" onClick={(e) => go(e, "inizio")}>Monolith</a>
        <nav>
          <a href="#versioni" onClick={(e) => go(e, "versioni")}>Versioni</a>
          <a className="bar-dl" href={latest.url} rel="noopener">Scarica</a>
        </nav>
      </div>
    </header>
  );
}

function VersionRow({ v }) {
  const [open, setOpen] = useState(false);
  const isLatest = v === latest;
  return (
    <li className="ver">
      <div className="ver-top">
        <div>
          <span className="vn">{v.version}</span>
          {isLatest && <span className="tag">Ultima</span>}
          <span className="vd">{dateIt(v.date)}{v.size ? ` · ${mb(v.size)}` : ""}</span>
        </div>
        {v.url ? <a className={isLatest ? "apk apk-on" : "apk"} href={v.url} rel="noopener" aria-label={`Scarica l'APK della versione ${v.version}`}>APK</a> : null}
      </div>
      <ul className="notes">
        {(open ? v.notes : v.notes.slice(0, 1)).map((n) => <li key={n}>{n}</li>)}
      </ul>
      {v.notes.length > 1 && (
        <button type="button" className="more" aria-expanded={open} onClick={() => setOpen(!open)}>
          {open ? "Mostra meno" : v.notes.length === 2 ? "+ 1 altra novità" : `+ altre ${v.notes.length - 1} novità`}
        </button>
      )}
    </li>
  );
}

const FAQ = [
  ["Su quali telefoni funziona?", "Android 8.0 o più recente. Su iPhone non c'è."],
  ["Perché non è sul Play Store?", "Si scarica solo da qui. Per questo Android chiede il permesso di installarla."],
  ["È sicura?", "Ogni APK è compilato in automatico dal codice pubblico su GitHub. Play Protect avvisa solo perché non arriva dal Play Store."],
  ["Dove stanno i miei dati?", "Sul tuo telefono. Senza backup né codice di recupero, se perdi il telefono il profilo non torna."],
  ["Serve un'email o il numero?", "No. Scegli un nome utente: gli amici ti trovano con quello o con il codice QR."],
  ["In che lingue è?", "Italiano, inglese, francese, tedesco, russo, polacco e ceco."],
];

export default function App() {
  const groups = useMemo(() => {
    const g = [
      { id: "2", name: "Monolith 2", list: [] },
      { id: "1", name: "Monolith 1", list: [] },
      { id: "0", name: "Le origini", list: [] },
    ];
    versions.forEach((v) => (g.find((x) => x.id === v.version.split(".")[0]) ?? g[2]).list.push(v));
    return g.filter((x) => x.list.length);
  }, []);
  useEffect(() => {
    const h = location.hash.slice(1);
    if (h) setTimeout(() => document.getElementById(h)?.scrollIntoView(), 200);
  }, []);
  const vt = themeOf(N), ft = themeOf(N + 1);
  return (
    <>
      <Bar />
      <main>
        {SCENES.map((s, i) => <Scene key={s.id} s={s} index={i} />)}

        <section id="versioni" className="page" data-t={vt === DARK ? "dark" : "light"} style={{ background: css(vt.bg), color: css(vt.ink), "--ink": css(vt.ink), "--bg": css(vt.bg) }}>
          <div className="text">
            <span className="kicker">Versioni</span>
            <h2 className="title">Tutte le versioni</h2>
            <p className="lead">Dalla più recente. Le vecchie restano qui se vuoi tornare indietro.</p>
            {groups.map((g) => (
              <div className="group" key={g.id}>
                <span className="kicker small">{g.name}</span>
                <ul className="vers">{g.list.map((v) => <VersionRow key={v.version} v={v} />)}</ul>
              </div>
            ))}
          </div>
        </section>

        <section id="domande" className="page" data-t={ft === DARK ? "dark" : "light"} style={{ background: css(ft.bg), color: css(ft.ink), "--ink": css(ft.ink), "--bg": css(ft.bg) }}>
          <div className="text">
            <span className="kicker">Domande</span>
            <h2 className="title">Prima di chiedere</h2>
            <dl className="faq">
              {FAQ.map(([q, a]) => (
                <div key={q}><dt>{q}</dt><dd>{a}</dd></div>
              ))}
            </dl>
            <footer>
              <span>Monolith · di Christian «Xoushal» Vedovi</span>
              <a href={REPO} rel="noopener">Codice sorgente</a>
            </footer>
          </div>
        </section>
      </main>
    </>
  );
}
