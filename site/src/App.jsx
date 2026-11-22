import { useEffect, useMemo, useState } from "react";
import { MotionConfig, motion } from "framer-motion";
import versions from "./versions.json";
import { Glyph } from "./Glyphs.jsx";
import { Hero } from "./Hero.jsx";
import { Tour, Phone } from "./Tour.jsx";

const REPO = "https://github.com/Xoushal-from-Aprilia/Monolith";
const MONTHS = ["gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno", "luglio", "agosto", "settembre", "ottobre", "novembre", "dicembre"];
const dateIt = (iso) => { const [y, m, d] = iso.split("-").map(Number); return `${d} ${MONTHS[m - 1]} ${y}`; };
const mb = (b) => (b / 1048576).toLocaleString("it-IT", { maximumFractionDigits: 1, minimumFractionDigits: 1 }) + " MB";

const FEATURES = [
  { g: Glyph.chat, t: "Chat", d: "Private e di gruppo, con vocali, foto a vista unica, sticker fatti da te, sondaggi, tris, forza 4, impiccato, dadi e una lavagna per disegnare insieme. Messaggi effimeri, programmati e fissati." },
  { g: Glyph.agenda, t: "Agenda", d: "Settimana e mese, eventi ricorrenti con più promemoria, inviti a cui gli amici rispondono, conto alla rovescia e meteo del giorno. Se in chat scrivi «sabato alle 9», diventa un evento con un tocco." },
  { g: Glyph.map, t: "Mappa", d: "Posizione dal vivo solo quando la accendi, «Sto arrivando» con distanza e orario, luoghi comuni che avvisano quando qualcuno arriva, e un avviso se un amico è a meno di 500 m." },
  { g: Glyph.together, t: "Insieme", d: "Spese condivise con saldi e pareggio, album di gruppo, abitudini da tenere insieme giorno dopo giorno e liste della spesa o delle cose da fare." },
  { g: Glyph.shard, t: "Shard", d: "Lo spazio del tuo gruppo: stanze scritte, vocali e private, bacheca, eventi, moderatori, messaggio di benvenuto e titoli personalizzati." },
  { g: Glyph.call, t: "Chiamate", d: "Voce e video fino a 4 persone, schermo condiviso, la stessa canzone che parte nello stesso momento per tutti, video in finestra mentre usi altro." },
  { g: Glyph.profile, t: "Profilo", d: "Copertina, stato di 24 ore, storie con musica, 37 distintivi disegnati a mano e un legame con ogni amico che cresce con i giorni in cui vi scrivete." },
  { g: Glyph.shield, t: "Sicurezza", d: "Tasto SOS che manda la posizione e la scheda medica, blocco con impronta o volto, codice di recupero, backup cifrato anche automatico ogni settimana." },
  { g: Glyph.compass, t: "Il telefono in mano", d: "Batti il cinque per aggiungere un amico, bussola che punta verso di lui, vocali che passano all'orecchio, scuoti per un trillo, una piccola vibrazione a ogni tocco." },
  { g: Glyph.sparkle, t: "La tua domenica", d: "Ogni domenica alle 18 la settimana in slide: con chi hai parlato di più, le emoji, il ritmo dei giorni, il legame più forte." },
];

const GALLERY = [
  ["shots/profilo.webp", "Il profilo di un amico"],
  ["shots/conversazione.webp", "Una conversazione"],
  ["shots/modifica.webp", "Modifica profilo"],
  ["shots/chat-scura.webp", "Chat, tema scuro"],
  ["shots/spese.webp", "Spese condivise"],
  ["shots/stanza.webp", "Una stanza di uno Shard"],
  ["shots/profilo-scuro.webp", "Profilo, tema scuro"],
  ["shots/abitudini.webp", "Abitudini"],
  ["shots/agenda-scura.webp", "Agenda, tema scuro"],
  ["shots/impostazioni.webp", "Impostazioni"],
];

const FAQ = [
  ["Su quali telefoni funziona?", "Su Android 8.0 o più recente. L'APK pesa circa 23 MB. Su iPhone non c'è: Monolith è solo per Android."],
  ["Perché non è sul Play Store?", "Perché è un progetto personale distribuito solo qui. Per questo Android ti chiede il permesso di installare un'app scaricata dal browser."],
  ["È sicuro installarlo?", "Ogni APK viene compilato in automatico dal codice sorgente pubblico su GitHub, che chiunque può leggere. Play Protect può avvisarti solo perché l'app non arriva dal Play Store."],
  ["Devo disinstallare per aggiornare?", "Per ora sì: ogni versione ha una firma diversa e Android non la installa sopra la vecchia. Prima fai un backup cifrato o copia il codice di recupero, poi disinstalla e installa la nuova."],
  ["Dove stanno i miei dati?", "Sul tuo telefono. Monolith non ha un server suo: i messaggi partono cifrati e li apre solo chi li riceve. Se perdi il telefono senza backup né codice di recupero, il profilo non si può recuperare."],
  ["Serve un'email o il numero?", "No. Scegli un nome utente e un'emoji: il profilo è una chiave che vive sul telefono, e gli amici ti trovano con il nome utente o il codice QR."],
  ["In che lingue è?", "Italiano, inglese, francese, tedesco, russo, polacco e ceco. La cambi in Impostazioni, alla voce Lingua."],
  ["Come scopro le novità?", "Torna su questa pagina: ogni nuova versione compare in cima all'elenco, con le novità. Nell'app le trovi anche in Impostazioni, nel Journal."],
];

function VersionRow({ v, latest }) {
  const [open, setOpen] = useState(false);
  const shown = open ? v.notes : v.notes.slice(0, 3);
  return (
    <div className="ver" id={`v-${v.version.replaceAll(".", "-")}`}>
      <div>
        <div className="vn num">{v.version}{latest && <span className="pill">Ultima</span>}</div>
        <div className="vd num">{dateIt(v.date)}</div>
      </div>
      <div>
        <ul>{shown.map((n, k) => <li key={k}>{n}</li>)}</ul>
        {v.notes.length > 3 && (
          <button type="button" className="more" onClick={() => setOpen(!open)} aria-expanded={open}>
            {open ? "Mostra meno" : `Mostra tutte le ${v.notes.length} novità`}
          </button>
        )}
      </div>
      <div className="dl">
        {v.url ? (
          <a className={latest ? "btn btn-primary btn-small" : "btn btn-ghost btn-small"} href={v.url} rel="noopener">
            <Glyph.download /> APK
          </a>
        ) : <small>APK non disponibile</small>}
        {v.size && <small className="num">{mb(v.size)} · build {v.build}</small>}
      </div>
    </div>
  );
}

function Versions() {
  const latest = versions[0];
  const groups = useMemo(() => {
    const g = [
      { id: "2", name: "Monolith 2", sub: "Giochi, Shard nella barra, distintivi, profilo nuovo", list: [] },
      { id: "1", name: "Monolith 1", sub: "Riepilogo della domenica, legame, preferiti", list: [] },
      { id: "0", name: "Le origini", sub: "Dalla prima chat ai luoghi comuni", list: [] },
    ];
    versions.forEach((v) => (g.find((x) => x.id === v.version.split(".")[0]) ?? g[2]).list.push(v));
    return g.filter((x) => x.list.length);
  }, []);
  return (
    <>
      <div className="latest">
        <div>
          <span className="eyebrow" style={{ color: "inherit", opacity: 0.6 }}>Ultima versione</span>
          <div className="v num" style={{ marginTop: 12 }}>{latest.version}</div>
          <div className="meta num">{dateIt(latest.date)} · {mb(latest.size)} · build {latest.build}</div>
          <ul>{latest.notes.slice(0, 4).map((n, k) => <li key={k}>{n}</li>)}</ul>
        </div>
        <a className="btn btn-primary" href={latest.url} rel="noopener"><Glyph.download /> Scarica l'APK</a>
      </div>
      {groups.map((g) => (
        <div className="group" key={g.id}>
          <div className="group-head">
            <h3>{g.name}</h3>
            <span>{g.list.length === 1 ? "1 versione" : `${g.list.length} versioni`} · {g.sub}</span>
          </div>
          {g.list.map((v) => <VersionRow key={v.version} v={v} latest={v === latest} />)}
        </div>
      ))}
    </>
  );
}

const rise = { initial: { y: 26 }, whileInView: { y: 0 }, viewport: { once: true, margin: "-60px" }, transition: { duration: 0.8, ease: [0.16, 1, 0.3, 1] } };

export default function App() {
  const latest = versions[0];
  const [toast, setToast] = useState("");
  const go = (id) => document.getElementById(id)?.scrollIntoView({ behavior: "smooth", block: "start" });
  useEffect(() => {
    const h = location.hash.slice(1);
    if (h) setTimeout(() => go(h), 300);
    const on = () => go(location.hash.slice(1));
    window.addEventListener("hashchange", on);
    return () => window.removeEventListener("hashchange", on);
  }, []);
  const copy = async () => {
    try { await navigator.clipboard.writeText(latest.url); setToast("Link dell'APK copiato"); }
    catch { setToast("Non riesco a copiare: tieni premuto il tasto Scarica e copia il link"); }
    setTimeout(() => setToast(""), 2600);
  };
  const nav = (e, id) => { e.preventDefault(); go(id); try { history.replaceState(null, "", `#${id}`); } catch { /* frame without history */ } };

  return (
    <MotionConfig reducedMotion="user">
      <nav className="nav">
        <div className="wrap">
          <a className="brand" href="#top" onClick={(e) => { e.preventDefault(); window.scrollTo({ top: 0, behavior: "smooth" }); }}>
            <span className="brand-mark" aria-hidden="true" /> Monolith
          </a>
          <div className="nav-links">
            <a href="#funzioni" onClick={(e) => nav(e, "funzioni")}>Funzioni</a>
            <a href="#installa" onClick={(e) => nav(e, "installa")}>Installa</a>
            <a href="#versioni" onClick={(e) => nav(e, "versioni")}>Versioni</a>
            <a href="#domande" onClick={(e) => nav(e, "domande")}>Domande</a>
          </div>
          <a className="btn btn-primary btn-small" href={latest.url} rel="noopener"><Glyph.download /> {latest.version}</a>
        </div>
      </nav>

      <Hero latest={latest} sizeText={mb(latest.size)} dateText={dateIt(latest.date)} onInstall={() => go("installa")} />

      <section id="schede">
        <div className="wrap">
          <motion.div className="sec-head" {...rise}>
            <div><span className="kicker">Le cinque schede</span><h2>Una barra, tutto quello che fate insieme.</h2></div>
            <p>Monolith si apre su cinque schede in basso. Tocca una scheda per vederla sul telefono.</p>
          </motion.div>
          <Tour />
        </div>
      </section>

      <section id="funzioni">
        <div className="wrap">
          <motion.div className="sec-head" {...rise}>
            <div><span className="kicker">Funzioni</span><h2>Dentro c'è molto. Tutto in bianco e nero.</h2></div>
            <p>Ogni aggiornamento aggiunge qualcosa scelto insieme. Questo è quello che trovi oggi nella {latest.version}.</p>
          </motion.div>
          <div className="features">
            {FEATURES.map((f) => (
              <motion.article className="feature" key={f.t} {...rise}>
                <f.g />
                <h3>{f.t}</h3>
                <p>{f.d}</p>
              </motion.article>
            ))}
          </div>
        </div>
      </section>

      <section id="schermate" style={{ paddingBottom: "clamp(48px, 6vw, 80px)" }}>
        <div className="wrap">
          <motion.div className="sec-head" {...rise}>
            <div><span className="kicker">Schermate</span><h2>Come appare, chiaro o scuro.</h2></div>
            <p>Schermate vere dell'app con dati di prova. Il tema segue quello del telefono, oppure lo scegli in Impostazioni.</p>
          </motion.div>
        </div>
        <div className="gallery" aria-label="Schermate di Monolith">
          <div className="gallery-track">
            {[...GALLERY, ...GALLERY].map(([src, cap], k) => (
              <figure key={k} aria-hidden={k >= GALLERY.length}>
                <Phone small src={src} alt={cap} />
                <figcaption>{cap}</figcaption>
              </figure>
            ))}
          </div>
        </div>
      </section>

      <section id="distintivi">
        <div className="wrap split">
          <motion.div {...rise}>
            <span className="kicker">Distintivi</span>
            <h2 style={{ fontSize: "clamp(38px, 5.2vw, 66px)" }}>37 distintivi, disegnati uno per uno.</h2>
            <p className="mute" style={{ marginTop: 18, fontSize: 17, maxWidth: "32em" }}>
              Si sbloccano usando l'app: il primo messaggio, cento giorni di fila con un amico, una chiamata di un'ora, uno Shard con dieci persone.
              Ne scegli tre da mostrare sotto il tuo nome, e vedi quelli dei tuoi amici nel loro profilo.
            </p>
          </motion.div>
          <motion.img {...rise} className="badges-img" src="shots/distintivi.webp" alt="La collezione dei distintivi di Monolith" loading="lazy" />
        </div>
      </section>

      <section id="privacy">
        <div className="wrap split">
          <motion.div {...rise}>
            <span className="kicker">Privacy</span>
            <h2 style={{ fontSize: "clamp(38px, 5.2vw, 66px)" }}>Quello che scrivi resta tra voi.</h2>
          </motion.div>
          <div className="points">
            {[
              [Glyph.key, "Il profilo vive sul telefono", "Niente email, niente numero, niente password da ricordare: il tuo profilo è una chiave salvata solo sul tuo telefono."],
              [Glyph.shield, "Messaggi cifrati", "Monolith non ha un server suo. I messaggi partono cifrati dal tuo telefono e li apre solo chi li riceve."],
              [Glyph.eye, "Niente pubblicità", "Nessuna pubblicità e nessun tracciamento. La posizione si condivide solo quando la accendi tu, e solo con chi scegli."],
            ].map(([G, t, d]) => (
              <motion.div className="point" key={t} {...rise}>
                <span className="ico"><G /></span>
                <div><h3>{t}</h3><p>{d}</p></div>
              </motion.div>
            ))}
          </div>
        </div>
      </section>

      <section id="installa">
        <div className="wrap">
          <motion.div className="sec-head" {...rise}>
            <div><span className="kicker">Tutorial</span><h2>Installarla in quattro passi.</h2></div>
            <p>Monolith non passa dal Play Store: si scarica qui come file APK. Fai tutto dal telefono, ci vogliono due minuti.</p>
          </motion.div>
          <div className="steps">
            <motion.div className="step" {...rise}>
              <span className="step-n num">01</span>
              <h3>Scarica l'APK</h3>
              <p>Dal telefono tocca «Scarica». Se il browser avvisa che il file potrebbe essere dannoso, scegli «Scarica comunque».</p>
              <div className="mock"><div className="row"><span>Monolith-{latest.build}.apk</span><span className="mute num">{mb(latest.size)}</span></div><div className="act">Scarica comunque</div></div>
            </motion.div>
            <motion.div className="step" {...rise}>
              <span className="step-n num">02</span>
              <h3>Consenti l'installazione</h3>
              <p>Apri il file dalla notifica o dall'app File, nella cartella Download. La prima volta Android ti manda nelle impostazioni: attiva «Consenti da questa fonte» e torna indietro.</p>
              <div className="mock"><div className="row"><span>Consenti da questa fonte</span><span className="sw" /></div></div>
            </motion.div>
            <motion.div className="step" {...rise}>
              <span className="step-n num">03</span>
              <h3>Installa</h3>
              <p>Tocca «Installa». Se Play Protect dice che non conosce l'app, apri «Altri dettagli» e scegli «Installa comunque»: succede a tutte le app fuori dal Play Store.</p>
              <div className="mock"><div className="row"><span className="mute">Altri dettagli</span><span className="act">Installa comunque</span></div></div>
            </motion.div>
            <motion.div className="step" {...rise}>
              <span className="step-n num">04</span>
              <h3>Apri Monolith</h3>
              <p>Un breve filmato ti mostra tutte le funzioni. Poi scegli il nome utente e un'emoji, e concedi i permessi che ti servono: notifiche, e la posizione solo se vuoi la mappa.</p>
              <div className="mock"><div className="row"><span>@il-tuo-nome</span><span className="act">Avanti</span></div></div>
            </motion.div>
          </div>

          <motion.div className="note" {...rise} id="aggiornare">
            <span className="ico"><Glyph.warn /></span>
            <div>
              <h3>Aggiornare da una versione vecchia</h3>
              <p className="mute">Per ora ogni versione ha una firma diversa, quindi Android non la installa sopra quella che hai. Così non perdi niente:</p>
              <ol>
                <li>In Monolith apri <b>Impostazioni → Backup cifrato</b> e salva il file con una password. In alternativa copia il <b>codice di recupero</b>: riporta profilo e amici, non le chat.</li>
                <li>Disinstalla Monolith.</li>
                <li>Installa la nuova versione con i passi qui sopra.</li>
                <li>All'apertura tocca <b>Ho già un profilo Monolith</b> e scegli il backup o il codice.</li>
              </ol>
            </div>
          </motion.div>

          <div className="firsts">
            {[
              [Glyph.friends, "Aggiungi un amico", "Scheda Amici: cerca il suo nome utente, inquadra il suo codice QR, oppure battete il cinque con i telefoni vicini."],
              [Glyph.chat, "Scrivi e chiama", "Dal profilo di un amico tocca Scrivi, oppure chiama e videochiama. In chat il tasto per allegare apre foto, sticker, giochi e lavagna."],
              [Glyph.shard, "Crea uno Shard", "Nella scheda Shard creane uno: dagli un nome, aggiungi stanze scritte e vocali e invita il tuo gruppo."],
              [Glyph.profile, "Sistema il profilo", "Nel tuo profilo tocca Modifica in alto a destra: immagine, bio, copertina e i tre distintivi in vetrina."],
            ].map(([G, t, d]) => (
              <motion.div key={t} {...rise}><G /><h3>{t}</h3><p>{d}</p></motion.div>
            ))}
          </div>
        </div>
      </section>

      <section id="versioni">
        <div className="wrap">
          <motion.div className="sec-head" {...rise}>
            <div><span className="kicker">Download</span><h2>Tutte le versioni.</h2></div>
            <p>Raggruppate per serie, dalla più recente. Le versioni vecchie restano qui se ti serve tornare indietro.</p>
          </motion.div>
          <Versions />
          <div style={{ display: "flex", gap: 12, flexWrap: "wrap", marginTop: 32 }}>
            <button type="button" className="btn btn-ghost btn-small" onClick={copy}><Glyph.copy /> Copia il link dell'ultima versione</button>
          </div>
        </div>
      </section>

      <section id="domande">
        <div className="wrap">
          <motion.div className="sec-head" {...rise}>
            <div><span className="kicker">Domande</span><h2>Prima di chiedere.</h2></div>
            <p>Le risposte alle domande che arrivano più spesso.</p>
          </motion.div>
          <div className="faq">
            {FAQ.map(([q, a]) => (
              <motion.div key={q} {...rise}><h3>{q}</h3><p>{a}</p></motion.div>
            ))}
          </div>
        </div>
      </section>

      <footer>
        <div className="wrap">
          <span className="brand"><span className="brand-mark" aria-hidden="true" /> Monolith</span>
          <span>Ideata e fatta da Christian «Xoushal» Vedovi</span>
          <span><a href={REPO} rel="noopener">Codice sorgente su GitHub</a></span>
        </div>
      </footer>
      {toast && <div className="toast" role="status">{toast}</div>}
    </MotionConfig>
  );
}
