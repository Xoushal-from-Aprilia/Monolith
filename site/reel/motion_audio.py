"""Soundtrack of the motion video: an original 120 BPM track plus the sound effects at the cues.

usage: python3 motion_audio.py cues.json out.wav
Everything is synthesized here (no samples), so the audio is free to use anywhere.
Bars start at 1, 3, 5 ... seconds: the big hits of the video land on downbeats.
"""
import json
import sys

import numpy as np
from scipy import signal

SR = 48000
cfg = json.load(open(sys.argv[1]))
TOTAL = cfg["total"]
N = int(round(TOTAL * SR))
rng = np.random.default_rng(3)

L = np.zeros(N); R = np.zeros(N)          # dry mix
VL = np.zeros(N); VR = np.zeros(N)        # reverb send
SC = np.ones(N)                           # sidechain gain from the kicks


def tt(d):
    return np.arange(int(d * SR)) / SR


def noise(d):
    return rng.uniform(-1, 1, int(d * SR))


def sos(kind, f, order=2):
    return signal.butter(order, f, kind, fs=SR, output="sos")


def hp(x, f, o=2): return signal.sosfilt(sos("highpass", f, o), x)
def lp(x, f, o=2): return signal.sosfilt(sos("lowpass", f, o), x)
def bp(x, lo, hi, o=2): return signal.sosfilt(sos("bandpass", [lo, hi], o), x)


def sweep_lp(x, f0, f1, curve=2.0):
    """one-pole low-pass whose cutoff moves from f0 to f1"""
    n = len(x)
    k = np.linspace(0, 1, n) ** curve
    fc = f0 * (f1 / f0) ** k
    a = 1 - np.exp(-2 * np.pi * fc / SR)
    y = np.empty(n); acc = 0.0
    for i in range(n):
        acc += a[i] * (x[i] - acc)
        y[i] = acc
    return y


def put(t0, x, g=1.0, pan=0.0, send=0.0, side=None):
    """adds a mono (or (l, r) tuple) sound at t0 seconds"""
    i = int(round(t0 * SR))
    if i >= N:
        return
    xl, xr = (x, x) if not isinstance(x, tuple) else x
    gl, gr = g * np.sqrt(0.5 * (1 - pan)), g * np.sqrt(0.5 * (1 + pan))
    j = min(N, i + len(xl))
    seg_l, seg_r = xl[: j - i], xr[: j - i]
    if side is not None:
        seg_l = seg_l * side[i:j]; seg_r = seg_r * side[i:j]
    L[i:j] += gl * seg_l; R[i:j] += gr * seg_r
    if send:
        VL[i:j] += gl * send * seg_l; VR[i:j] += gr * send * seg_r


def hz(note):
    return 440.0 * 2 ** ((note - 69) / 12)


# ---------------------------------------------------------------- instruments
def kick():
    t = tt(0.5)
    f = 46 + 120 * np.exp(-t * 30)
    body = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 7.5)
    click = hp(noise(0.5), 2500) * np.exp(-t * 500) * 0.35
    return np.tanh((body + click) * 1.6) / np.tanh(1.6)


def clap():
    t = tt(0.45)
    n = bp(noise(0.45), 900, 3500)
    e = sum(np.where(t >= d, np.exp(-(t - d) * 180), 0) for d in (0, 0.011, 0.022)) * 0.6 + np.exp(-t * 16) * 0.5
    return n * e


def hat(open_=False):
    d = 0.35 if open_ else 0.07
    t = tt(d)
    return hp(noise(d), 7500, 4) * np.exp(-t * (11 if open_ else 70))


def snare():
    t = tt(0.25)
    return bp(noise(0.25), 1200, 7000) * np.exp(-t * 26) + np.sin(2 * np.pi * 190 * t) * np.exp(-t * 32) * 0.5


def crash(d=2.6):
    t = tt(d)
    n = hp(noise(d), 3500, 2)
    return n * np.exp(-t * 1.9) * (1 - np.exp(-t * 400))


def saw(f, t, detune=0.0):
    ph = f * (2 ** (detune / 1200)) * t + rng.uniform()
    return 2 * (ph % 1) - 1


def bass_note(note, d):
    t = tt(d)
    f = hz(note)
    x = 0.65 * saw(f, t) + 0.6 * np.sin(2 * np.pi * f * t)
    x = lp(x, 700)
    env = (1 - np.exp(-t * 300)) * np.exp(-t * 1.8)
    env[-int(0.01 * SR):] *= np.linspace(1, 0, int(0.01 * SR))
    return x * env


def pad_chord(notes, d, att=0.35):
    t = tt(d)
    l = np.zeros(len(t)); r = np.zeros(len(t))
    for n in notes:
        f = hz(n)
        l += saw(f, t, -7) + 0.6 * saw(f, t, 5)
        r += saw(f, t, 7) + 0.6 * saw(f, t, -5)
    env = np.minimum(1, t / att) * np.minimum(1, (d - t) / 0.3)
    k = 1 / (len(notes) * 1.6)
    return lp(l, 1400) * env * k, lp(r, 1400) * env * k


def pluck(note, d=0.45, bright=1.0):
    t = tt(d)
    f = hz(note)
    x = np.sin(2 * np.pi * f * t) + 0.45 * np.sin(2 * np.pi * 2 * f * t) * np.exp(-t * 14) + 0.2 * np.sin(2 * np.pi * 3 * f * t) * np.exp(-t * 24)
    x = lp(x, 3500 * bright)
    return x * (1 - np.exp(-t * 900)) * np.exp(-t * 8)


def bell(f, d=1.2, decay=4.0):
    t = tt(d)
    x = np.sin(2 * np.pi * f * t) + 0.5 * np.sin(2 * np.pi * f * 2.76 * t) * np.exp(-t * 6) + 0.25 * np.sin(2 * np.pi * f * 5.4 * t) * np.exp(-t * 12)
    return x * (1 - np.exp(-t * 2000)) * np.exp(-t * decay)


# ---------------------------------------------------------------- the score
BEAT = 0.5
CHORDS = {  # midi notes: pad voicing, bass root
    "Am": ([57, 60, 64, 69], 33), "F": ([53, 57, 60, 65], 29),
    "C": ([55, 60, 64, 67], 36), "G": ([55, 59, 62, 67], 31),
}
PROG = ["Am", "F", "C", "G", "Am", "F", "C", "G", "Am", "F", "C"]   # bars at 1, 3, ... 21
bar_at = lambda b: 1 + 2 * b

full = lambda t: (3 <= t < 13) or (17 <= t < 23)       # sections with drums
kicks = []
for b in range(11):
    t0 = bar_at(b)
    for q in range(4):
        tb = t0 + q * BEAT
        if full(tb) or tb in (1.0, 15.0):
            kicks.append(tb)
kicks.append(23.0)
for tk in kicks:
    i = int(tk * SR)
    d = np.arange(N - i) / SR
    SC[i:] = np.minimum(SC[i:], 1 - 0.55 * np.exp(-d / 0.11))
for tk in kicks:
    put(tk, kick(), 0.95)

for b in range(11):
    t0 = bar_at(b)
    for q in (1, 3):
        if full(t0 + q * BEAT):
            put(t0 + q * BEAT, clap(), 0.42, pan=0.05, send=0.25)
    for e in range(8):
        te = t0 + e * BEAT / 2
        if full(te) and e % 2 == 1:
            put(te, hat(e == 7), 0.16 if e != 7 else 0.12, pan=0.25)
        if (2.0 <= te < 3) or (13 <= te < 15):           # soft hats before the drops
            put(te, hat(), 0.1 + 0.12 * ((te - 13) / 2 if te >= 13 else 0), pan=0.2)
    for s in range(16):
        ts = t0 + s * BEAT / 4
        if full(ts) and s % 2 == 0:
            put(ts, hat(), 0.05, pan=-0.3)

# bass: an 8th-note line on the root, octave on the off beats
for b, name in enumerate(PROG):
    t0 = bar_at(b)
    root = CHORDS[name][1]
    if t0 < 3:
        put(t0, bass_note(root, 2.0), 0.5, side=SC)
        continue
    if 13 <= t0 < 17:
        put(t0, bass_note(root, 1.9), 0.35, side=SC)
        continue
    for e in range(8):
        n = root + (12 if e in (3, 7) else 0)
        put(t0 + e * BEAT / 2, bass_note(n, 0.24), 0.5, side=SC)

# pad: whole bars, a pickup swell before the first one
put(0.0, pad_chord(CHORDS["Am"][0], 1.0, att=0.9), 0.5, send=0.4)
for b, name in enumerate(PROG):
    put(bar_at(b), pad_chord(CHORDS[name][0], 2.05), 0.42, send=0.35, side=SC)

# the hook: a plucked arpeggio, two octaves up
PATTERN = [0, 2, 1, 2, 3, 2, 1, 2]
for b, name in enumerate(PROG):
    t0 = bar_at(b)
    if t0 < 3:
        continue
    notes = CHORDS[name][0]
    soft = 13 <= t0 < 17
    for e, k in enumerate(PATTERN):
        n = notes[k] + 12
        put(t0 + e * BEAT / 2, pluck(n, bright=0.45 if soft else 1.0), 0.22 if soft else 0.3, pan=(-0.3 if e % 2 else 0.3), send=0.3, side=SC)

# builds: noise risers and a snare roll into 17
def riser(t0, d, g):
    x = sweep_lp(noise(d), 200, 9000, 1.6) * np.linspace(0, 1, int(d * SR)) ** 2
    put(t0, hp(x, 150), g, send=0.4)
riser(0.0, 1.0, 0.5)
riser(13.3, 1.7, 0.6)
riser(15.4, 1.6, 0.35)
roll = [15.5 + k * 0.25 for k in range(2)] + [16.0 + k * 0.125 for k in range(4)] + [16.5 + k * 0.0625 for k in range(8)]
for k, tr in enumerate(roll):
    put(tr, snare(), 0.12 + 0.3 * k / len(roll), pan=0.1, send=0.2)

for tc, g in ((3.0, 0.3), (7.0, 0.2), (15.0, 0.35), (17.0, 0.35), (19.0, 0.4), (23.0, 0.4)):
    put(tc, crash(), g, pan=-0.15, send=0.3)

# the ending: one last chord that rings out
put(23.0, pad_chord(CHORDS["Am"][0] + [76], 2.0, att=0.02), 0.5, send=0.6)
for k, n in enumerate([69, 72, 76, 81]):
    put(23.0 + k * 0.03, pluck(n, d=1.6), 0.2, send=0.6)
put(23.0, bass_note(33, 1.8), 0.5)


# ---------------------------------------------------------------- sound effects
def fx_tick(f=2000):
    t = tt(0.06)
    return np.sin(2 * np.pi * f * t) * np.exp(-t * 220) + hp(noise(0.06), 3000) * np.exp(-t * 500) * 0.3


def fx_pop(f=600):
    t = tt(0.16)
    fr = f * (1 + 1.3 * np.exp(-t * 55))
    return np.sin(2 * np.pi * np.cumsum(fr) / SR) * (1 - np.exp(-t * 3000)) * np.exp(-t * 26)


def fx_whoosh(d=0.5, up=1):
    x = noise(d)
    x = sweep_lp(x, 300, 7000, 1.3) if up else sweep_lp(x, 7000, 300, 0.7)
    x = hp(x, 180)
    t = np.linspace(0, 1, len(x))
    peak = 0.8 if up else 0.25
    env = np.where(t < peak, (t / peak) ** 2, ((1 - t) / (1 - peak)) ** 1.5)
    y = x * env
    pan = np.linspace(-0.6, 0.6, len(y))
    return y * np.sqrt(0.5 * (1 - pan)) * 1.4, y * np.sqrt(0.5 * (1 + pan)) * 1.4


def fx_swoosh():
    d = 0.32
    x = sweep_lp(noise(d), 600, 9000, 0.8)
    t = np.linspace(0, 1, len(x))
    return hp(x, 400) * np.sin(np.pi * t) ** 2 * 1.3


def fx_impact():
    t = tt(1.8)
    f = 30 + 55 * np.exp(-t * 9)
    boom = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 3.2)
    thump = lp(noise(1.8), 250) * np.exp(-t * 10) * 1.5
    return np.tanh((boom + thump) * 1.4)


def fx_thud():
    t = tt(0.4)
    f = 55 + 80 * np.exp(-t * 25)
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 11) + lp(noise(0.4), 400) * np.exp(-t * 30) * 0.6


def fx_ring():
    out = np.zeros(int(0.9 * SR))
    for k, n in enumerate([88, 93, 88, 93]):
        p = pluck(n, d=0.5)
        i = int(k * 0.09 * SR)
        out[i:i + len(p)] += p[: len(out) - i]
    return out


def fx_chime():
    out = np.zeros(int(1.4 * SR))
    for k, n in enumerate([84, 88, 91, 96]):
        b = bell(hz(n), 1.2, 5)
        i = int(k * 0.05 * SR)
        out[i:i + len(b)] += b[: len(out) - i] * 0.5
    return out


def fx_ping():
    return bell(hz(93), 1.5, 3.0) * 0.8 + bell(hz(100), 1.5, 4.0) * 0.3


def fx_shimmer():
    out = np.zeros(int(1.4 * SR))
    for k, n in enumerate([81, 84, 88, 93, 96, 100]):
        b = bell(hz(n), 1.0, 5)
        i = int(k * 0.035 * SR)
        out[i:i + len(b)] += b[: len(out) - i] * 0.35
    return out


def fx_shatter():
    d = 0.7
    t = tt(d)
    out = hp(noise(d), 2500) * np.exp(-t * 14) * 0.8
    for _ in range(26):
        i = int(rng.uniform(0, 0.3) * SR)
        b = bell(rng.uniform(2500, 7000), 0.25, rng.uniform(25, 55)) * rng.uniform(0.1, 0.3)
        out[i:i + len(b)] += b[: len(out) - i]
    return out


def fx_tink(f=1800):
    return bell(f, 0.6, 9)


def fx_click():
    out = np.zeros(int(0.12 * SR))
    for k, f in enumerate((1100, 1500)):
        t = tt(0.05)
        c = bp(noise(0.05), 1000, 4500) * np.exp(-t * 380) + np.sin(2 * np.pi * f * t) * np.exp(-t * 300) * 0.5
        i = int(k * 0.055 * SR)
        out[i:i + len(c)] += c
    return out


FX = {
    "tick": (lambda p: fx_tick(p.get("f", 2000)), 0.0),
    "pop": (lambda p: fx_pop(p.get("f", 600)), 0.15),
    "whoosh": (lambda p: fx_whoosh(p.get("dur", 0.5), p.get("up", 1)), 0.2),
    "swoosh": (lambda p: fx_swoosh(), 0.2),
    "impact": (lambda p: fx_impact(), 0.35),
    "thud": (lambda p: fx_thud(), 0.1),
    "ring": (lambda p: fx_ring(), 0.3),
    "chime": (lambda p: fx_chime(), 0.4),
    "ping": (lambda p: fx_ping(), 0.4),
    "shimmer": (lambda p: fx_shimmer(), 0.5),
    "shatter": (lambda p: fx_shatter(), 0.35),
    "tink": (lambda p: fx_tink(p.get("f", 1800)), 0.4),
    "click": (lambda p: fx_click(), 0.1),
}
LEVEL = {"tick": 0.35, "pop": 0.5, "whoosh": 0.5, "swoosh": 0.4, "impact": 0.9, "thud": 0.6, "ring": 0.3,
         "chime": 0.35, "ping": 0.4, "shimmer": 0.3, "shatter": 0.45, "tink": 0.3, "click": 0.45}
for kind, t0, p in cfg["cues"]:
    make, send = FX[kind]
    put(t0, make(p), LEVEL[kind] * p.get("g", 1.0), pan=p.get("pan", 0.0), send=send)

# ---------------------------------------------------------------- reverb and master
def ir(d=2.2, seed=0):
    r = np.random.default_rng(seed)
    t = np.arange(int(d * SR)) / SR
    x = r.uniform(-1, 1, len(t)) * np.exp(-t * 3.0)
    x = lp(x, 5000)
    x[: int(0.012 * SR)] = 0
    return x / np.sqrt(np.sum(x ** 2))

wet_l = signal.fftconvolve(VL, ir(seed=1))[:N]
wet_r = signal.fftconvolve(VR, ir(seed=2))[:N]
mix_l = hp(L + wet_l * 0.9, 28)
mix_r = hp(R + wet_r * 0.9, 28)

fade = np.ones(N)
f0 = int(24.3 * SR)
fade[f0:] = np.linspace(1, 0, N - f0) ** 2
mix = np.stack([mix_l * fade, mix_r * fade], axis=1)
mix /= np.max(np.abs(mix)) + 1e-9
mix = np.tanh(mix * 1.8) / np.tanh(1.8)       # gentle glue
mix *= 0.89 / np.max(np.abs(mix))              # -1 dBFS peak

from scipy.io import wavfile
wavfile.write(sys.argv[2], SR, (mix * 32767).astype(np.int16))
rms = 20 * np.log10(np.sqrt(np.mean(mix ** 2)) + 1e-12)
print(f"{sys.argv[2]}: {TOTAL:.1f}s, peak -1.0 dBFS, rms {rms:.1f} dBFS")
