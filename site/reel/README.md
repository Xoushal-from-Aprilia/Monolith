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
