"""Soundtrack of the «Seguici» video: a lighter 120 BPM bed in D minor that leaves room for a voice,
with the effects at the video's cues.

usage: python3 follow_audio.py cues.json out.wav
Bars start at 1, 3, 5 ... s; the big hits (1, 15, 23 s) land on downbeats.
"""
import json
import sys

from synth import (Mix, bass_note, clap, crash, hat, kick, pad_chord, place_cues, pluck, riser, snare)

cfg = json.load(open(sys.argv[1]))
mix = Mix(cfg["total"])
BEAT = 0.5
CHORDS = {"Dm": ([57, 62, 65, 69], 38), "Bb": ([58, 62, 65, 70], 34), "F": ([57, 60, 65, 69], 41), "C": ([55, 60, 64, 67], 36)}
PROG = ["Dm", "Bb", "F", "C", "Dm", "Bb", "F", "C", "Dm", "Bb", "F"]       # bars at 1, 3 ... 21
bar_at = lambda b: 1 + 2 * b
groove = lambda t: (3 <= t < 13) or (15 <= t < 23)

kicks = [1.0, 23.0]
for b in range(len(PROG)):
    for q in range(4):
        tb = bar_at(b) + q * BEAT
        if groove(tb):
            kicks.append(tb)
mix.duck(kicks, depth=0.5)
for tk in kicks:
    mix.put(tk, kick(), 0.8)

for b in range(len(PROG)):
    t0 = bar_at(b)
    for q in (1, 3):
        if groove(t0 + q * BEAT):
            mix.put(t0 + q * BEAT, clap(), 0.22, pan=0.05, send=0.25)
    for e in range(8):
        te = t0 + e * BEAT / 2
        if groove(te) and e % 2 == 1:
            mix.put(te, hat(e == 7), 0.12, pan=0.25)
        if 2.0 <= te < 3 or 13 <= te < 15:
            mix.put(te, hat(), 0.08 + (0.1 * (te - 13) / 2 if te >= 13 else 0), pan=0.2)
    for s in range(16):
        ts = t0 + s * BEAT / 4
        if groove(ts) and s % 2 == 0:
            mix.put(ts, hat(), 0.04, pan=-0.3)

for b, name in enumerate(PROG):
    t0 = bar_at(b)
    root = CHORDS[name][1]
    if not groove(t0):
        mix.put(t0, bass_note(root, 2.0, cut=600), 0.42, side=True)
        continue
    for e in range(8):
        mix.put(t0 + e * BEAT / 2, bass_note(root + (12 if e in (3, 6) else 0), 0.24, cut=600), 0.42, side=True)

mix.put(0.0, pad_chord(CHORDS["Dm"][0], 1.0, att=0.9, cut=1100), 0.4, send=0.4)
for b, name in enumerate(PROG):
    mix.put(bar_at(b), pad_chord(CHORDS[name][0], 2.05, cut=1100), 0.32, send=0.35, side=True)

PATTERN = [3, 2, 0, 2, 3, 2, 1, 2]
for b, name in enumerate(PROG):
    t0 = bar_at(b)
    if t0 < 3:
        continue
    g = 0.14 if t0 < 9 else 0.17 if t0 < 13 else 0.12 if t0 < 15 else 0.2
    for e, k in enumerate(PATTERN):
        mix.put(t0 + e * BEAT / 2, pluck(CHORDS[name][0][k] + 12, bright=0.55), g, pan=(-0.3 if e % 2 else 0.3), send=0.3, side=True)

mix.put(0.0, riser(1.0), 0.4, send=0.4)
mix.put(13.2, riser(1.8), 0.5, send=0.4)
roll = [14.0 + k * 0.25 for k in range(2)] + [14.5 + k * 0.125 for k in range(2)] + [14.75 + k * 0.0625 for k in range(4)]
for k, tr in enumerate(roll):
    mix.put(tr, snare(), 0.1 + 0.22 * k / len(roll), pan=0.1, send=0.2)
for tc, g in ((3.0, 0.22), (15.0, 0.3), (23.0, 0.35)):
    mix.put(tc, crash(), g, pan=-0.15, send=0.3)

# the last chord rings out under the logo
mix.put(23.0, pad_chord(CHORDS["Dm"][0] + [74], 3.0, att=0.02, cut=1500), 0.45, send=0.6)
for k, n in enumerate([62, 65, 69, 74]):
    mix.put(23.0 + k * 0.03, pluck(n, d=1.8), 0.18, send=0.6)
mix.put(23.0, bass_note(38, 2.0), 0.45)

place_cues(mix, cfg["cues"])
mix.master(sys.argv[2], fade_from=25.8)
print(sys.argv[2], "written")
