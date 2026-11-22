// Line glyphs drawn for the site, in the same monoline style as the badges inside the app.
const S = { fill: "none", stroke: "currentColor", strokeWidth: 1.8, strokeLinecap: "round", strokeLinejoin: "round" };

export const Glyph = {
  chat: () => (
    <svg viewBox="0 0 24 24" {...S}><rect x="3" y="4" width="18" height="12" rx="4" /><path d="M8 16l-2 4 5-4" /><circle cx="9" cy="10" r=".6" fill="currentColor" /><circle cx="12" cy="10" r=".6" fill="currentColor" /><circle cx="15" cy="10" r=".6" fill="currentColor" /></svg>
  ),
  agenda: () => (
    <svg viewBox="0 0 24 24" {...S}><rect x="3.5" y="5" width="17" height="15" rx="3" /><path d="M3.5 10h17M8 3v4M16 3v4" /><circle cx="15.5" cy="15" r="1.4" fill="currentColor" /></svg>
  ),
  map: () => (
    <svg viewBox="0 0 24 24" {...S}><path d="M12 21s-6-5.6-6-11a6 6 0 0 1 12 0c0 5.4-6 11-6 11z" /><circle cx="12" cy="10" r="2.2" /></svg>
  ),
  friends: () => (
    <svg viewBox="0 0 24 24" {...S}><circle cx="9" cy="9" r="3.2" /><path d="M3.5 19c.8-3 3-4.6 5.5-4.6s4.7 1.6 5.5 4.6" /><circle cx="16.5" cy="8" r="2.6" /><path d="M16 12.6c2.3 0 4 1.3 4.6 3.9" /></svg>
  ),
  shard: () => (
    <svg viewBox="0 0 24 24" {...S}><path d="M12.6 2.5l7 7.2-8.2 11.8-7-8.6z" /><path d="M12.6 2.5l-1.2 19M4.4 12.9l15.2-3.2" strokeWidth="1.1" /></svg>
  ),
  together: () => (
    <svg viewBox="0 0 24 24" {...S}><path d="M12 4v16M7 20h10M5 7h14" /><path d="M5 7l-2.5 6a2.5 2.5 0 0 0 5 0z M19 7l-2.5 6a2.5 2.5 0 0 0 5 0z" /></svg>
  ),
  call: () => (
    <svg viewBox="0 0 24 24" {...S}><path d="M6.5 3.5l3 3.5-2 2.2a11 11 0 0 0 7.3 7.3l2.2-2 3.5 3-1.6 2.6c-.5.7-1.4 1-2.2.8C10.6 19.5 4.5 13.4 3.1 7.3c-.2-.8.1-1.7.8-2.2z" /></svg>
  ),
  profile: () => (
    <svg viewBox="0 0 24 24" {...S}><path d="M12 2.8l8 4.6v9.2l-8 4.6-8-4.6V7.4z" /><path d="M12 7.5l1.3 2.8 3 .3-2.3 2 .7 3-2.7-1.6-2.7 1.6.7-3-2.3-2 3-.3z" strokeWidth="1.3" /></svg>
  ),
  shield: () => (
    <svg viewBox="0 0 24 24" {...S}><path d="M12 3l7.5 3v5.5c0 4.6-3.2 8-7.5 9.5-4.3-1.5-7.5-4.9-7.5-9.5V6z" /><path d="M8.8 12l2.2 2.2 4.4-4.6" /></svg>
  ),
  compass: () => (
    <svg viewBox="0 0 24 24" {...S}><circle cx="12" cy="12" r="8.5" /><path d="M14.8 9.2l-1.6 4-4 1.6 1.6-4z" fill="currentColor" strokeWidth="1.2" /></svg>
  ),
  sparkle: () => (
    <svg viewBox="0 0 24 24" {...S}><path d="M12 3c.6 4.8 1.8 6.2 6.5 7-4.7.8-5.9 2.2-6.5 7-.6-4.8-1.8-6.2-6.5-7 4.7-.8 5.9-2.2 6.5-7z" fill="currentColor" strokeWidth="1" /><path d="M18.5 16.5l.6 2.4 2.4.6-2.4.6-.6 2.4-.6-2.4-2.4-.6 2.4-.6z" fill="currentColor" strokeWidth=".8" /></svg>
  ),
  key: () => (
    <svg viewBox="0 0 24 24" {...S}><circle cx="8" cy="8" r="4.2" /><path d="M11 11l9 9M16.2 16.2l2.2-2.2M13.8 13.8l2-2" /></svg>
  ),
  cloud: () => (
    <svg viewBox="0 0 24 24" {...S}><path d="M3.5 12.5L12 4l8.5 8.5" /><path d="M6.5 9.5V20h11V9.5" /><path d="M10 20v-5h4v5" /></svg>
  ),
  eye: () => (
    <svg viewBox="0 0 24 24" {...S}><path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z" /><path d="M4 20L20 4" /></svg>
  ),
  download: () => (
    <svg viewBox="0 0 24 24" {...S} strokeWidth="2.2"><path d="M12 4v11M7 10.5l5 5 5-5M5 20h14" /></svg>
  ),
  arrow: () => (
    <svg viewBox="0 0 24 24" {...S} strokeWidth="2"><path d="M5 12h14M13 6l6 6-6 6" /></svg>
  ),
  warn: () => (
    <svg viewBox="0 0 24 24" {...S} strokeWidth="2"><path d="M12 8v5M12 16.5v.5" /><circle cx="12" cy="12" r="9" /></svg>
  ),
  copy: () => (
    <svg viewBox="0 0 24 24" {...S}><rect x="8" y="8" width="12" height="12" rx="3" /><path d="M16 8V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h2" /></svg>
  ),
  qr: () => (
    <svg viewBox="0 0 24 24" {...S}><rect x="3.5" y="3.5" width="6" height="6" rx="1" /><rect x="14.5" y="3.5" width="6" height="6" rx="1" /><rect x="3.5" y="14.5" width="6" height="6" rx="1" /><path d="M14.5 14.5h2.5v2.5M20.5 14.5v6h-3.5M14.5 20.5v-1" /></svg>
  ),
  hand: () => (
    <svg viewBox="0 0 24 24" {...S}><rect x="6" y="11" width="10" height="10" rx="4" /><path d="M8 11V5M10.5 11V3.8M13 11V4.4M15.5 11V6.5M6 14.5l-2.6-2.8" /><path d="M18.5 6l2-1.2M19 9.5h2.4" /></svg>
  ),
};
