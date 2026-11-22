# Sito di Monolith

Pagina per scaricare l'APK di Monolith (React + Vite + framer-motion), pubblicata come artifact su claude.ai.

## Aggiornarlo dopo una nuova versione
1. Aggiungi `"x.y.z": <numero build>` in `data/builds.json` (la build ufficiale della versione).
2. `python3 gen_versions.py` → rigenera `src/versions.json` dal Journal dell'app e dalle release di GitHub.
3. (Facoltativo) aggiorna le schermate in `public/shots/` dagli screenshot della CI (`[shots]`), ritagliando i 188 px in alto.
4. `npm install && npm run build` → `dist/`.
5. Ripubblica `dist/` sullo stesso artifact.
