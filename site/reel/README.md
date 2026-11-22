# Reel

The intro film cut for Instagram: 1080×1920, 30 fps, about 36 seconds, Italian.
Text and drawings stay between y 290 and 1320, clear of the Reels header, caption and buttons.

- `reel.src.html`: the reel; the drawings come from `../src/film.js`.
- `build_reel.py <out.html>`: one self-contained HTML file, with Inter inlined (`npm i @fontsource/inter`).
- `render.mjs <page.html> video <dir>`: one MP4 per scene with puppeteer-core and ffmpeg, then
  `ffmpeg -f concat -safe 0 -i <dir>/list.txt -c copy -movflags +faststart monolith-reel.mp4`.
  `render.mjs <page.html> stills <dir> 3.3 6.1 ...` saves single frames to check.

The page plays the reel full screen (for a screen recording) and can save it as MP4 while it plays.
