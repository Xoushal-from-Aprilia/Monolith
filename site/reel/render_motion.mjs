// Renders the reel page frame by frame.
//   node motion.mjs <page.html> stills <outdir> <t1> <t2> ...   → PNG of each moment
//   node motion.mjs <page.html> video <outdir>                    → one MP4 per scene, then the joined reel
import puppeteer from "puppeteer-core";
import { spawn } from "node:child_process";
import fs from "node:fs";
import path from "node:path";

const [, , file, mode, outDir, ...times] = process.argv;
fs.mkdirSync(outDir, { recursive: true });
const browser = await puppeteer.launch({ executablePath: "/usr/bin/google-chrome", args: ["--no-sandbox", "--disable-gpu", "--font-render-hinting=none"], headless: "new" });
const page = await browser.newPage();
await page.setViewport({ width: 540, height: 960 });
const errors = [];
page.on("pageerror", (e) => errors.push(String(e)));
page.on("console", (m) => { if (m.type() === "error") errors.push(m.text()); });
await page.goto("file://" + path.resolve(file), { waitUntil: "load" });
await page.waitForFunction(() => window.MOTION && window.MOTION.ready, { timeout: 20000 });
const info = await page.evaluate(() => ({ FPS: MOTION.FPS, TOTAL: MOTION.TOTAL, SCENES: MOTION.SCENES }));

const grab = (t, type, q) => page.evaluate((t, type, q) => {
  MOTION.frame(t);
  return document.getElementById("c").toDataURL(type, q).split(",")[1];
}, t, type, q);

if (mode === "cues") {
  const cues = await page.evaluate(() => ({ total: MOTION.TOTAL, cues: MOTION.CUES }));
  fs.writeFileSync(path.join(outDir, "cues.json"), JSON.stringify(cues));
} else if (mode === "stills") {
  for (const t of times) {
    const b64 = await grab(Number(t), "image/png");
    fs.writeFileSync(path.join(outDir, `t${t}.png`), Buffer.from(b64, "base64"));
  }
} else {
  const { FPS, TOTAL, SCENES } = info;
  const total = Math.round(TOTAL * FPS);
  const list = [];
  for (let i = 0; i < SCENES.length; i++) {
    const a = Math.round(SCENES[i].start * FPS);
    const b = i === SCENES.length - 1 ? total : Math.round(SCENES[i + 1].start * FPS);
    const name = `scene${String(i).padStart(2, "0")}.mp4`, tmp = path.join(outDir, `tmp-${name}`), fin = path.join(outDir, name);
    const ff = spawn("ffmpeg", ["-y", "-loglevel", "error", "-f", "image2pipe", "-framerate", String(FPS), "-c:v", "mjpeg", "-i", "-",
      "-c:v", "libx264", "-preset", "veryfast", "-crf", process.env.CRF || "16", ...(process.env.MAXRATE ? ["-maxrate", process.env.MAXRATE, "-bufsize", String(2 * parseInt(process.env.MAXRATE)) + "M"] : []), "-pix_fmt", "yuv420p", "-r", String(FPS), "-colorspace", "bt709", "-color_primaries", "bt709", "-color_trc", "bt709", tmp]);
    const done = new Promise((res, rej) => ff.on("exit", (c) => (c === 0 ? res() : rej(new Error("ffmpeg " + c)))));
    for (let f = a; f < b; f++) {
      const b64 = await grab(f / FPS, "image/jpeg", 0.92);
      if (!ff.stdin.write(Buffer.from(b64, "base64"))) await new Promise((r) => ff.stdin.once("drain", r));
    }
    ff.stdin.end();
    await done;
    fs.renameSync(tmp, fin);
    list.push(`file '${name}'`);
    process.stdout.write(`${name} ${b - a} frames\n`);
  }
  fs.writeFileSync(path.join(outDir, "list.txt"), list.join("\n") + "\n");
}
console.log("errors:", errors.length ? errors.join("\n") : "none");
await browser.close();
