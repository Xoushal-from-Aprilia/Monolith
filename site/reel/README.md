# Reel

The intro film cut for Instagram: 1080×1920, 30 fps, about 36 seconds, Italian.
Text and drawings stay between y 290 and 1320, clear of the Reels header, caption and buttons.

- `reel.src.html`: the reel; the drawings come from `../src/film.js`.
- `build_reel.py <out.html>`: one self-contained HTML file, with Inter inlined (`npm i @fontsource/inter`).
- `render.mjs <page.html> video <dir>`: one MP4 per scene with puppeteer-core and ffmpeg, then
  `ffmpeg -f concat -safe 0 -i <dir>/list.txt -c copy -movflags +faststart monolith-reel.mp4`.
  `render.mjs <page.html> stills <dir> 3.3 6.1 ...` saves single frames to check.

The page plays the reel full screen (for a screen recording) and can save it as MP4 while it plays.

## Motion video (25 s, with sound)

A separate motion graphic, not the intro film: rise of the monolith, four verbs, the phone with chat and map,
the bond counter and its badge, the Shard, safety, the logo. 120 BPM, big hits on downbeats (1, 3, 15, 17, 19 s).

- `motion.src.html`: the animation, a pure function of time; `CUES` lists every sound effect.
- `python3 build_reel.py out.html motion.src.html`, then
  `node render_motion.mjs out.html cues <dir>` (writes `cues.json`) and `node render_motion.mjs out.html video <dir>`.
- `python3 motion_audio.py <dir>/cues.json track.wav`: the music and effects, all synthesized (no samples).
- Join: concat the scenes, then mux with the track (`-c:a aac -b:a 256k`), x264 crf 21, maxrate 14M.

## «Seguici» video (27 s, for a voiceover)

Made to grow the TikTok and Instagram pages: «Questa è Monolith» → what it is → «ogni follow è un pezzo»
(the monolith built from falling blocks, progress towards the release) → Segui on TikTok and Instagram,
notifications, a comment → logo «Costruiamolo insieme». Content stays in y 300–1250 and x 160–920,
clear of both apps' buttons and captions.

- `follow.src.html` (uses `motionkit.js`, the drawing kit shared by the motion videos; `build_reel.py` inlines it).
- `follow_audio.py` (uses `synth.py`): a lighter bed in D minor, then set to −17 LUFS so a voice sits on top:
  measure with `ffmpeg -af ebur128`, then `volume=<gain>dB,alimiter=limit=0.84`.
- Render with `CRF=21 MAXRATE=14M node render_motion.mjs …`, then concat with `-c:v copy` and the AAC track.
