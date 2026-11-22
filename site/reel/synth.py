"""Small synthesizer for the Monolith videos: instruments, sound effects, reverb and master.

Everything is generated from scratch (no samples), so the audio is free to use anywhere.
"""
import numpy as np
from scipy import signal
from scipy.io import wavfile

SR = 48000
rng = np.random.default_rng(3)


def tt(d):
    return np.arange(int(d * SR)) / SR


def noise(d):
    return rng.uniform(-1, 1, int(d * SR))


def _sos(kind, f, order=2):
    return signal.butter(order, f, kind, fs=SR, output="sos")


def hp(x, f, o=2): return signal.sosfilt(_sos("highpass", f, o), x)
def lp(x, f, o=2): return signal.sosfilt(_sos("lowpass", f, o), x)
def bp(x, lo, hi, o=2): return signal.sosfilt(_sos("bandpass", [lo, hi], o), x)


def hz(note):
    return 440.0 * 2 ** ((note - 69) / 12)


def sweep_lp(x, f0, f1, curve=2.0):
    """one-pole low-pass whose cutoff moves from f0 to f1"""
    n = len(x)
    k = np.linspace(0, 1, n) ** curve
    a = 1 - np.exp(-2 * np.pi * (f0 * (f1 / f0) ** k) / SR)
    y = np.empty(n); acc = 0.0
    for i in range(n):
        acc += a[i] * (x[i] - acc)
        y[i] = acc
    return y


class Mix:
    def __init__(self, total):
        self.N = int(round(total * SR))
        self.L = np.zeros(self.N); self.R = np.zeros(self.N)
        self.VL = np.zeros(self.N); self.VR = np.zeros(self.N)
        self.SC = np.ones(self.N)

    def put(self, t0, x, g=1.0, pan=0.0, send=0.0, side=False):
        i = int(round(t0 * SR))
        if i >= self.N or i < 0:
            return
        xl, xr = (x, x) if not isinstance(x, tuple) else x
        gl, gr = g * np.sqrt(0.5 * (1 - pan)), g * np.sqrt(0.5 * (1 + pan))
        j = min(self.N, i + len(xl))
        a, b = xl[: j - i], xr[: j - i]
        if side:
            a = a * self.SC[i:j]; b = b * self.SC[i:j]
        self.L[i:j] += gl * a; self.R[i:j] += gr * b
        if send:
            self.VL[i:j] += gl * send * a; self.VR[i:j] += gr * send * b

    def duck(self, times, depth=0.55, tau=0.11):
        for tk in times:
            i = int(tk * SR)
            d = np.arange(self.N - i) / SR
            self.SC[i:] = np.minimum(self.SC[i:], 1 - depth * np.exp(-d / tau))

    def master(self, path, fade_from=None, wet=0.9):
        def ir(seed, d=2.2):
            r = np.random.default_rng(seed)
            t = np.arange(int(d * SR)) / SR
            x = lp(r.uniform(-1, 1, len(t)) * np.exp(-t * 3.0), 5000)
            x[: int(0.012 * SR)] = 0
            return x / np.sqrt(np.sum(x ** 2))
        l = hp(self.L + signal.fftconvolve(self.VL, ir(1))[: self.N] * wet, 28)
        r = hp(self.R + signal.fftconvolve(self.VR, ir(2))[: self.N] * wet, 28)
        mix = np.stack([l, r], axis=1)
        if fade_from is not None:
            f0 = int(fade_from * SR)
            mix[f0:] *= (np.linspace(1, 0, self.N - f0) ** 2)[:, None]
        mix /= np.max(np.abs(mix)) + 1e-9
        mix = np.tanh(mix * 1.8) / np.tanh(1.8)
        mix *= 0.89 / np.max(np.abs(mix))
        wavfile.write(path, SR, (mix * 32767).astype(np.int16))
        return mix


# ---------------------------------------------------------------- instruments
def kick(punch=1.0):
    t = tt(0.5)
    f = 46 + 120 * punch * np.exp(-t * 30)
    body = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 7.5)
    click = hp(noise(0.5), 2500) * np.exp(-t * 500) * 0.3
    return np.tanh((body + click) * 1.6) / np.tanh(1.6)


def clap():
    t = tt(0.45)
    e = sum(np.where(t >= d, np.exp(-(t - d) * 180), 0) for d in (0, 0.011, 0.022)) * 0.6 + np.exp(-t * 16) * 0.5
    return bp(noise(0.45), 900, 3500) * e


def hat(open_=False):
    d = 0.35 if open_ else 0.07
    return hp(noise(d), 7500, 4) * np.exp(-tt(d) * (11 if open_ else 70))


def snare():
    t = tt(0.25)
    return bp(noise(0.25), 1200, 7000) * np.exp(-t * 26) + np.sin(2 * np.pi * 190 * t) * np.exp(-t * 32) * 0.5


def crash(d=2.6):
    t = tt(d)
    return hp(noise(d), 3500) * np.exp(-t * 1.9) * (1 - np.exp(-t * 400))


def saw(f, t, detune=0.0):
    ph = f * (2 ** (detune / 1200)) * t + rng.uniform()
    return 2 * (ph % 1) - 1


def bass_note(note, d, cut=700):
    t = tt(d)
    f = hz(note)
    x = lp(0.65 * saw(f, t) + 0.6 * np.sin(2 * np.pi * f * t), cut)
    env = (1 - np.exp(-t * 300)) * np.exp(-t * 1.8)
    env[-int(0.01 * SR):] *= np.linspace(1, 0, int(0.01 * SR))
    return x * env


def pad_chord(notes, d, att=0.35, cut=1400):
    t = tt(d)
    l = np.zeros(len(t)); r = np.zeros(len(t))
    for n in notes:
        f = hz(n)
        l += saw(f, t, -7) + 0.6 * saw(f, t, 5)
        r += saw(f, t, 7) + 0.6 * saw(f, t, -5)
    env = np.minimum(1, t / att) * np.minimum(1, (d - t) / 0.3)
    k = 1 / (len(notes) * 1.6)
    return lp(l, cut) * env * k, lp(r, cut) * env * k


def pluck(note, d=0.45, bright=1.0):
    t = tt(d)
    f = hz(note)
    x = np.sin(2 * np.pi * f * t) + 0.45 * np.sin(4 * np.pi * f * t) * np.exp(-t * 14) + 0.2 * np.sin(6 * np.pi * f * t) * np.exp(-t * 24)
    return lp(x, 3500 * bright) * (1 - np.exp(-t * 900)) * np.exp(-t * 8)


def bell(f, d=1.2, decay=4.0):
    t = tt(d)
    x = np.sin(2 * np.pi * f * t) + 0.5 * np.sin(2 * np.pi * f * 2.76 * t) * np.exp(-t * 6) + 0.25 * np.sin(2 * np.pi * f * 5.4 * t) * np.exp(-t * 12)
    return x * (1 - np.exp(-t * 2000)) * np.exp(-t * decay)


def riser(d):
    x = sweep_lp(noise(d), 200, 9000, 1.6) * np.linspace(0, 1, int(d * SR)) ** 2
    return hp(x, 150)


# ---------------------------------------------------------------- sound effects
def _seq(parts, d):
    out = np.zeros(int(d * SR))
    for t0, x in parts:
        i = int(t0 * SR)
        out[i:i + len(x)] += x[: len(out) - i]
    return out


def fx_tick(f=2000):
    t = tt(0.06)
    return np.sin(2 * np.pi * f * t) * np.exp(-t * 220) + hp(noise(0.06), 3000) * np.exp(-t * 500) * 0.3


def fx_pop(f=600):
    t = tt(0.16)
    fr = f * (1 + 1.3 * np.exp(-t * 55))
    return np.sin(2 * np.pi * np.cumsum(fr) / SR) * (1 - np.exp(-t * 3000)) * np.exp(-t * 26)


def fx_whoosh(d=0.5, up=1):
    x = sweep_lp(noise(d), 300, 7000, 1.3) if up else sweep_lp(noise(d), 7000, 300, 0.7)
    x = hp(x, 180)
    t = np.linspace(0, 1, len(x))
    peak = 0.8 if up else 0.25
    y = x * np.where(t < peak, (t / peak) ** 2, ((1 - t) / (1 - peak)) ** 1.5)
    pan = np.linspace(-0.6, 0.6, len(y))
    return y * np.sqrt(0.5 * (1 - pan)) * 1.4, y * np.sqrt(0.5 * (1 + pan)) * 1.4


def fx_swoosh():
    x = sweep_lp(noise(0.32), 600, 9000, 0.8)
    return hp(x, 400) * np.sin(np.pi * np.linspace(0, 1, len(x))) ** 2 * 1.3


def fx_impact():
    t = tt(1.8)
    f = 30 + 55 * np.exp(-t * 9)
    boom = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 3.2)
    return np.tanh((boom + lp(noise(1.8), 250) * np.exp(-t * 10) * 1.5) * 1.4)


def fx_thud():
    t = tt(0.4)
    f = 55 + 80 * np.exp(-t * 25)
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 11) + lp(noise(0.4), 400) * np.exp(-t * 30) * 0.6


def fx_block(f=1.0):
    """a heavy piece landing on the stack: low knock plus a wooden click"""
    t = tt(0.35)
    fr = (70 + 90 * np.exp(-t * 40)) * f
    knock = np.sin(2 * np.pi * np.cumsum(fr) / SR) * np.exp(-t * 16)
    click = bp(noise(0.35), 1500 * f, 5000) * np.exp(-t * 160) * 0.5
    return knock + click + bell(880 * f, 0.35, 18) * 0.12


def fx_ping():
    return bell(hz(93), 1.5, 3.0) * 0.8 + bell(hz(100), 1.5, 4.0) * 0.3


def fx_shimmer():
    return _seq([(k * 0.035, bell(hz(n), 1.0, 5) * 0.35) for k, n in enumerate([81, 84, 88, 93, 96, 100])], 1.4)


def fx_click():
    def one(f):
        t = tt(0.05)
        return bp(noise(0.05), 1000, 4500) * np.exp(-t * 380) + np.sin(2 * np.pi * f * t) * np.exp(-t * 300) * 0.5
    return _seq([(0, one(1100)), (0.055, one(1500))], 0.12)


FX = {
    "tick": (lambda p: fx_tick(p.get("f", 2000)), 0.0, 0.35),
    "pop": (lambda p: fx_pop(p.get("f", 600)), 0.15, 0.5),
    "whoosh": (lambda p: fx_whoosh(p.get("dur", 0.5), p.get("up", 1)), 0.2, 0.5),
    "swoosh": (lambda p: fx_swoosh(), 0.2, 0.4),
    "impact": (lambda p: fx_impact(), 0.35, 0.9),
    "thud": (lambda p: fx_thud(), 0.1, 0.6),
    "block": (lambda p: fx_block(p.get("f", 1.0)), 0.15, 0.5),
    "ping": (lambda p: fx_ping(), 0.4, 0.4),
    "shimmer": (lambda p: fx_shimmer(), 0.5, 0.3),
    "click": (lambda p: fx_click(), 0.1, 0.45),
}


def place_cues(mix, cues):
    for kind, t0, p in cues:
        make, send, level = FX[kind]
        mix.put(t0, make(p), level * p.get("g", 1.0), pan=p.get("pan", 0.0), send=send)
