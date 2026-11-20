"""Monolith sound kit: calm, soft, tiny. Everything is synthesised here, then encoded to OGG Vorbis."""
import os
import subprocess
import sys

import numpy as np
from scipy.signal import butter, fftconvolve, sosfilt

SR = 32000
OUT = sys.argv[1] if len(sys.argv) > 1 else "out"
WAV = os.path.join(OUT, "wav")
os.makedirs(WAV, exist_ok=True)
rng = np.random.default_rng(7)


def t_axis(dur):
    return np.arange(int(dur * SR)) / SR


def env(dur, attack=0.004, decay=0.5, release=0.02):
    t = t_axis(dur)
    a = np.clip(t / max(attack, 1e-4), 0, 1)
    a = 0.5 - 0.5 * np.cos(np.pi * a)  # smooth attack
    d = np.exp(-t / decay)
    r = np.clip((dur - t) / release, 0, 1)
    return a * d * r


def note(freq):
    names = {"C": -9, "D": -7, "E": -5, "F": -4, "G": -2, "A": 0, "B": 2}
    n, octv = freq[:-1], int(freq[-1])
    semi = names[n[0]] + (1 if n.endswith("#") else -1 if n.endswith("b") and len(n) > 1 else 0)
    return 440.0 * 2 ** ((semi + 12 * (octv - 4)) / 12)


def partials(f, dur, ratios, amps, decays, attack=0.004, detune=0.0):
    t = t_axis(dur)
    s = np.zeros_like(t)
    for r, a, d in zip(ratios, amps, decays):
        ph = rng.uniform(0, 2 * np.pi)
        s += a * np.sin(2 * np.pi * f * r * t + ph) * np.exp(-t / d)
        if detune:
            s += a * 0.6 * np.sin(2 * np.pi * f * r * (1 + detune) * t + ph) * np.exp(-t / d)
    a = np.clip(t / attack, 0, 1)
    # every note fades out before it ends: no clicks when it is cut
    rel = min(0.35, dur * 0.3)
    r = np.clip((dur - t) / rel, 0, 1)
    return s * (0.5 - 0.5 * np.cos(np.pi * a)) * (0.5 - 0.5 * np.cos(np.pi * r))


def marimba(f, dur=1.2):
    return partials(f, dur, [1, 3.93, 9.2], [1, 0.25, 0.06], [0.42, 0.09, 0.03], attack=0.002)


def kalimba(f, dur=1.4):
    return partials(f, dur, [1, 2.0, 5.4], [1, 0.12, 0.18], [0.55, 0.3, 0.05], attack=0.0015)


def bell(f, dur=2.5):
    return partials(f, dur, [1, 2.0, 2.76, 4.07, 5.4], [1, 0.45, 0.35, 0.18, 0.1], [1.4, 0.9, 0.7, 0.35, 0.2], attack=0.002)


def musicbox(f, dur=1.6):
    return partials(f, dur, [1, 3.0, 6.2], [1, 0.18, 0.05], [0.7, 0.18, 0.06], attack=0.001)


def bowl(f, dur=5.0):
    s = partials(f, dur, [1, 2.71, 5.15, 8.5], [1, 0.5, 0.22, 0.07], [3.2, 2.0, 1.1, 0.5], attack=0.01, detune=0.0025)
    t = t_axis(dur)
    return s * (1 + 0.12 * np.sin(2 * np.pi * 0.9 * t))  # slow shimmer


def glass(f, dur=0.9):
    return partials(f, dur, [1, 2.32, 4.25], [1, 0.25, 0.08], [0.32, 0.12, 0.05], attack=0.001)


def pad(freqs, dur, attack=1.2, release=1.4):
    t = t_axis(dur)
    s = np.zeros_like(t)
    for f in freqs:
        for det in (-0.003, 0.0, 0.0035):
            s += np.sin(2 * np.pi * f * (1 + det) * t + rng.uniform(0, 6.28))
            s += 0.2 * np.sin(2 * np.pi * 2 * f * (1 + det) * t)
    up = np.clip(t / attack, 0, 1)
    dn = np.clip((dur - t) / release, 0, 1)
    shape = (0.5 - 0.5 * np.cos(np.pi * up)) * (0.5 - 0.5 * np.cos(np.pi * dn))
    return lowpass(s * shape / len(freqs), 2200)


def lowpass(x, fc, order=2):
    return sosfilt(butter(order, fc, "low", fs=SR, output="sos"), x)


def bandpass(x, lo, hi, order=2):
    return sosfilt(butter(order, [lo, hi], "band", fs=SR, output="sos"), x)


def reverb(x, seconds=1.6, wet=0.28, damp=3500):
    n = int(seconds * SR)
    t = np.arange(n) / SR
    ir = rng.standard_normal(n) * np.exp(-t * 6.9 / seconds)
    ir = lowpass(ir, damp)
    ir[: int(0.012 * SR)] = 0  # pre-delay
    ir /= np.sqrt(np.sum(ir ** 2))
    dry = np.concatenate([x, np.zeros(n)])
    y = fftconvolve(x, ir)
    y = np.concatenate([y, np.zeros(max(0, len(dry) - len(y)))])[: len(dry)]
    return (1 - wet) * dry + wet * y * 0.9


def place(track, sig, at):
    i = int(at * SR)
    end = min(len(track), i + len(sig))
    track[i:end] += sig[: end - i]


def canvas(dur):
    return np.zeros(int(dur * SR))


def fade_tail(x, sec=0.25):
    n = int(sec * SR)
    x = x.copy()
    x[-n:] *= np.linspace(1, 0, n) ** 2
    return x


def finish(x, peak_db, length=None):
    if length is not None:
        x = x[: int(length * SR)] if len(x) >= int(length * SR) else np.concatenate([x, np.zeros(int(length * SR) - len(x))])
    x = x - np.mean(x)
    x = fade_tail(x, min(0.25, len(x) / SR / 4))
    p = np.max(np.abs(x)) or 1
    return x / p * 10 ** (peak_db / 20)


def save(name, x):
    pcm = np.clip(x * 32767, -32768, 32767).astype("<i2")
    wav = os.path.join(WAV, name + ".wav")
    import wave
    with wave.open(wav, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    ogg = os.path.join(OUT, name + ".ogg")
    q = "1" if len(x) / SR > 2.5 else "2"
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", q, "-map_metadata", "-1", ogg], check=True)
    return ogg


S = {}

# ---------------------------------------------------------------- taps (tiny, quiet, no tail)
t = t_axis(0.07)
f = 1500 * np.exp(-t / 0.035) + 650          # water drop: falling pitch
S["tap_goccia"] = finish(np.sin(2 * np.pi * np.cumsum(f) / SR) * env(0.07, 0.001, 0.018, 0.01), -14)
wood = bandpass(rng.standard_normal(len(t_axis(0.05))), 700, 2600) * env(0.05, 0.0005, 0.007, 0.01)
wood += 0.6 * np.sin(2 * np.pi * 820 * t_axis(0.05)) * env(0.05, 0.0005, 0.012, 0.01)
S["tap_legno"] = finish(wood, -15)
S["tap_vetro"] = finish(glass(2900, 0.09) * env(0.09, 0.001, 0.03, 0.02), -18)
S["tap_soffio"] = finish(np.sin(2 * np.pi * 520 * t_axis(0.06)) * env(0.06, 0.004, 0.02, 0.015), -13)

# ---------------------------------------------------------------- ringtones (loop ~6 s, each loop ends quietly)
# Alba: marimba pentatonic arpeggio
tr = canvas(6.0)
seq = ["C5", "E5", "G5", "A5", "G5", "E5", "D5", "G5"]
for i, n in enumerate(seq):
    place(tr, marimba(note(n)) * (0.9 if i % 2 else 1.0), 0.05 + i * 0.32)
place(tr, marimba(note("C6"), 1.8) * 0.5, 0.05 + 8 * 0.32)
S["ring_alba"] = finish(reverb(tr, 1.4, 0.25), -4, 6.0)

# Ciotola: two singing-bowl strikes
tr = canvas(6.0)
place(tr, bowl(note("A3") * 1.0, 3.2), 0.02)
place(tr, bowl(note("E4"), 3.0) * 0.8, 3.0)
S["ring_ciotola"] = finish(reverb(tr, 2.0, 0.22, 2500), -4, 6.0)

# Risacca: a slow wave of sound with a chime on the crest
tr = canvas(6.0)
place(tr, pad([note("A3"), note("C4"), note("E4"), note("B4")], 5.2, 2.0, 2.6), 0.0)
place(tr, bell(note("E5"), 2.5) * 0.35, 1.9)
place(tr, bell(note("B5"), 2.5) * 0.22, 2.35)
S["ring_risacca"] = finish(reverb(tr, 2.2, 0.3, 3000), -4, 6.0)

# Carillon: music-box melody
tr = canvas(6.0)
mel = [("E6", 0), ("G6", 1), ("C7", 2), ("B6", 3), ("G6", 4), ("A6", 5.5), ("G6", 6), ("E6", 7)]
for n, beat in mel:
    place(tr, musicbox(note(n)), 0.05 + beat * 0.38)
S["ring_carillon"] = finish(reverb(tr, 1.6, 0.3, 5000), -5, 6.0)

# Kalimba: soft repeating figure
tr = canvas(6.0)
fig = ["D5", "A4", "F5", "A4", "E5", "A4", "C5", None, "D5", "A4", "F5", "G5"]
for i, n in enumerate(fig):
    if n:
        place(tr, kalimba(note(n)) * (1.0 if i % 2 == 0 else 0.7), 0.05 + i * 0.27)
S["ring_kalimba"] = finish(reverb(tr, 1.3, 0.22), -4, 6.0)

# ---------------------------------------------------------------- call sounds
tr = canvas(3.0)                    # waiting for the other side: two soft notes every 3 s
place(tr, bell(note("E5"), 1.6) * 0.8, 0.05)
place(tr, bell(note("A5"), 1.6) * 0.6, 0.45)
S["call_attesa"] = finish(reverb(tr, 1.0, 0.2), -10, 3.0)
tr = canvas(0.9)
place(tr, marimba(note("G5"), 0.5), 0.0)
place(tr, marimba(note("D6"), 0.6), 0.12)
S["call_connesso"] = finish(reverb(tr, 0.7, 0.18), -9, 0.9)
tr = canvas(1.0)
place(tr, marimba(note("D5"), 0.6), 0.0)
place(tr, marimba(note("G4"), 0.7), 0.16)
S["call_fine"] = finish(reverb(tr, 0.7, 0.18), -10, 1.0)

# ---------------------------------------------------------------- messages
t = t_axis(0.16)
f = 380 + 520 * (1 - np.exp(-t / 0.05))      # bubble: rising
S["msg_bolla"] = finish(reverb(np.sin(2 * np.pi * np.cumsum(f) / SR) * env(0.16, 0.003, 0.05, 0.03), 0.5, 0.15), -6, 0.6)
t = t_axis(0.25)
f = 1700 * np.exp(-t / 0.05) + 700
drop = np.sin(2 * np.pi * np.cumsum(f) / SR) * env(0.25, 0.001, 0.06, 0.05)
S["msg_goccia"] = finish(reverb(drop, 0.9, 0.25), -6, 1.0)
tr = canvas(1.6)
place(tr, bell(note("A5"), 1.4), 0.0)
place(tr, bell(note("E6"), 1.4) * 0.7, 0.14)
S["msg_campanella"] = finish(reverb(tr, 1.0, 0.22), -6, 1.6)
tr = canvas(0.9)
place(tr, marimba(note("E5"), 0.6), 0.0)
place(tr, marimba(note("A5"), 0.7), 0.11)
S["msg_legno"] = finish(reverb(tr, 0.6, 0.15), -5, 0.9)
tr = canvas(2.0)
place(tr, glass(note("C7"), 1.0) * 0.8, 0.0)
place(tr, glass(note("G6"), 1.2) * 0.6, 0.18)
S["msg_piuma"] = finish(reverb(tr, 1.6, 0.4, 6000), -7, 2.0)

# ---------------------------------------------------------------- meeting / arrival
tr = canvas(2.2)
for i, n in enumerate(["C5", "E5", "G5"]):
    place(tr, marimba(note(n), 1.0), i * 0.13)
place(tr, bell(note("C6"), 1.8) * 0.55, 0.42)
S["inc_ritrovo"] = finish(reverb(tr, 1.4, 0.25), -5, 2.2)
tr = canvas(3.0)
place(tr, bowl(note("D4"), 2.8) * 0.8, 0.0)
place(tr, bell(note("A5"), 2.0) * 0.35, 0.5)
S["inc_benvenuto"] = finish(reverb(tr, 1.8, 0.25, 3000), -5, 3.0)
tr = canvas(2.6)
place(tr, bell(note("G5"), 2.0), 0.0)
place(tr, bell(note("D6"), 2.0) * 0.8, 0.6)
S["inc_faro"] = finish(reverb(tr, 1.6, 0.28), -6, 2.6)
tr = canvas(2.4)
place(tr, pad([note("D4"), note("F#4"), note("A4")], 2.2, 0.5, 1.2) * 0.9, 0.0)
place(tr, kalimba(note("A5"), 1.2) * 0.5, 0.6)
place(tr, kalimba(note("D6"), 1.2) * 0.4, 0.85)
S["inc_onda"] = finish(reverb(tr, 1.6, 0.3), -6, 2.4)

# ---------------------------------------------------------------- reminders
tr = canvas(2.0)
for n, b in [("G6", 0), ("E6", 1), ("C6", 2), ("D6", 3)]:
    place(tr, musicbox(note(n)), b * 0.22)
S["rem_carillon"] = finish(reverb(tr, 1.2, 0.3, 5000), -6, 2.0)
tr = canvas(3.2)
place(tr, bowl(note("G3"), 3.2), 0.0)
S["rem_gong"] = finish(reverb(tr, 1.8, 0.2, 2500), -5, 3.2)
tr = canvas(1.8)
for i, n in enumerate(["C5", "G5", "E5"]):
    place(tr, kalimba(note(n), 1.2), i * 0.2)
S["rem_mattino"] = finish(reverb(tr, 1.2, 0.22), -5, 1.8)
tr = canvas(2.0)
place(tr, glass(note("E6"), 1.0), 0.0)
place(tr, glass(note("B6"), 1.0) * 0.7, 0.16)
place(tr, glass(note("E7"), 1.0) * 0.45, 0.32)
S["rem_chiaro"] = finish(reverb(tr, 1.4, 0.3, 6000), -7, 2.0)

total = 0
for name, x in S.items():
    o = save(name, x)
    sz = os.path.getsize(o)
    total += sz
    print(f"{name:16s} {len(x)/SR:5.2f}s {sz/1024:6.1f} KB")
print(f"TOTAL {total/1024:.1f} KB in {len(S)} files")
