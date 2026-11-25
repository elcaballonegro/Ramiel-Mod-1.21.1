#!/usr/bin/env python3
"""
Generates every texture and sound of the mod from code, so there are no binary files to hand-edit.
Run from the project root:   python3 tools/gen_assets.py
Needs: numpy, pillow, soundfile.   Safe to re-run; it overwrites.
Change a colour or a sound here, run it again, rebuild.
"""
import os
import numpy as np
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "ramiel")
rng = np.random.default_rng(618)


def path(*p):
    full = os.path.join(ROOT, *p)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    return full


# --------------------------------------------------------------------------------------------
# Periodic fractal noise (wraps in both directions, so the Earth has no seam)
# --------------------------------------------------------------------------------------------
def fractal(h, w, power=2.2, seed=0):
    r = np.random.default_rng(seed)
    spec = np.fft.fft2(r.standard_normal((h, w)))
    fy = np.fft.fftfreq(h)[:, None]
    fx = np.fft.fftfreq(w)[None, :]
    f = np.sqrt(fx * fx + fy * fy)
    f[0, 0] = 1.0
    spec = spec / f ** (power / 2 * 2 * 0.5 + 0.5)
    spec[0, 0] = 0
    out = np.real(np.fft.ifft2(spec))
    out -= out.min()
    return out / out.max()


# --------------------------------------------------------------------------------------------
# Textures
# --------------------------------------------------------------------------------------------
def crystal():
    """Near-white faceted noise. The renderer multiplies it with the blue vertex colours."""
    n = fractal(64, 64, 1.6, 1)
    facets = (np.floor(n * 5) / 5.0)
    v = 0.86 + 0.14 * facets
    img = np.zeros((64, 64, 4), dtype=np.uint8)
    img[..., 0] = (v * 242).astype(np.uint8)
    img[..., 1] = (v * 247).astype(np.uint8)
    img[..., 2] = 255
    img[..., 3] = 255
    Image.fromarray(img, "RGBA").save(path("textures", "entity", "crystal.png"))


def earth():
    h, w = 512, 1024
    height = fractal(h, w, 2.4, 11)
    detail = fractal(h, w, 1.8, 12)
    land_mask = (height * 0.8 + detail * 0.2) > 0.56
    lat = np.abs(np.linspace(-1, 1, h))[:, None] * np.ones((1, w))
    ocean_t = np.clip((height - 0.30) / 0.26, 0, 1)
    ocean = np.stack([10 + 25 * ocean_t, 40 + 80 * ocean_t, 105 + 90 * ocean_t], axis=-1)
    moist = fractal(h, w, 2.0, 13)
    green = np.stack([40 + 70 * (1 - moist), 90 + 70 * moist, 40 + 25 * moist], axis=-1)
    desert = np.stack([190 + 40 * detail, 160 + 40 * detail, 100 + 30 * detail], axis=-1)
    dry = (moist < 0.38) & (lat < 0.55)
    land = np.where(dry[..., None], desert, green)
    rock_t = np.clip((height - 0.68) / 0.2, 0, 1)[..., None]
    land = land * (1 - rock_t) + np.array([140, 130, 120]) * rock_t
    col = np.where(land_mask[..., None], land, ocean)
    ice = np.clip((lat - 0.80) / 0.1, 0, 1)[..., None]
    col = col * (1 - ice) + np.array([240, 245, 250]) * ice
    cloud = np.clip((fractal(h, w, 2.0, 14) - 0.5) * 3.2, 0, 1)[..., None] * 0.85
    col = col * (1 - cloud) + np.array([255, 255, 255]) * cloud
    img = np.zeros((h, w, 4), dtype=np.uint8)
    img[..., :3] = np.clip(col, 0, 255).astype(np.uint8)
    img[..., 3] = 255
    Image.fromarray(img, "RGBA").save(path("textures", "fx", "earth.png"))


def item_icons():
    # Plasma cell: a metal canister with a glowing cyan core
    c = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(c)
    d.rectangle([5, 1, 10, 14], fill=(54, 62, 80, 255))
    d.rectangle([4, 3, 11, 12], fill=(74, 84, 108, 255))
    d.rectangle([6, 3, 9, 12], fill=(40, 220, 255, 255))
    d.rectangle([7, 4, 8, 11], fill=(220, 255, 255, 255))
    d.rectangle([4, 1, 11, 2], fill=(110, 120, 140, 255))
    d.rectangle([4, 13, 11, 14], fill=(110, 120, 140, 255))
    c.save(path("textures", "item", "plasma_cell.png"))

    # Angel core: a red faceted gem
    g = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(g)
    d.polygon([(8, 0), (14, 7), (8, 15), (2, 7)], fill=(190, 20, 30, 255))
    d.polygon([(8, 0), (14, 7), (8, 8)], fill=(255, 90, 80, 255))
    d.polygon([(8, 0), (2, 7), (8, 8)], fill=(235, 50, 55, 255))
    d.polygon([(8, 15), (14, 7), (8, 8)], fill=(120, 10, 25, 255))
    d.line([(8, 2), (8, 6)], fill=(255, 220, 210, 255))
    g.save(path("textures", "item", "angel_core.png"))

    # Railcannon texture: a palette of 4x4-pixel swatches, the 3D model picks colours by UV
    r = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
    d = ImageDraw.Draw(r)
    palette = [
        (150, 158, 172), (96, 104, 120), (52, 58, 72), (30, 34, 44),
        (60, 230, 255), (20, 150, 190), (255, 255, 255), (230, 60, 50),
        (255, 170, 40), (80, 110, 160), (180, 190, 205), (20, 24, 32),
        (120, 220, 190), (40, 80, 70), (200, 120, 40), (70, 70, 80),
    ]
    for i, colr in enumerate(palette):
        x, y = (i % 4) * 4, (i // 4) * 4
        d.rectangle([x, y, x + 3, y + 3], fill=colr + (255,))
    r.save(path("textures", "item", "railcannon.png"))


# --------------------------------------------------------------------------------------------
# Sounds (synthesised; mono, 44.1 kHz, Ogg Vorbis)
# --------------------------------------------------------------------------------------------
SR = 44100


def t(seconds):
    return np.arange(int(SR * seconds)) / SR


def lowpass(x, cutoff):
    a = np.exp(-2 * np.pi * cutoff / SR)
    k = (1 - a) * a ** np.arange(int(SR / max(cutoff, 20) * 6))
    return np.convolve(x, k)[: len(x)]


def env(n, attack, release):
    e = np.ones(n)
    a, r = int(SR * attack), int(SR * release)
    if a:
        e[:a] = np.linspace(0, 1, a)
    if r:
        e[-r:] *= np.linspace(1, 0, r)
    return e


def norm(x, peak=0.9):
    return (x / (np.max(np.abs(x)) + 1e-9) * peak).astype("float32")


def save(name, x):
    import soundfile as sf
    sf.write(path("sounds", name + ".ogg"), norm(x), SR, format="OGG", subtype="VORBIS")


def sounds():
    # alarm: a two-tone siren, two cycles
    tt = t(2.4)
    freq = np.where((tt // 0.6) % 2 == 0, 640.0, 860.0)
    phase = 2 * np.pi * np.cumsum(freq) / SR
    siren = np.sign(np.sin(phase)) * 0.5 + np.sin(phase) * 0.5
    save("alarm", lowpass(siren, 3500) * env(len(tt), 0.02, 0.15))

    # charge: a rising whine with shimmer
    tt = t(3.2)
    f = 70 + 900 * (tt / tt[-1]) ** 2.2
    ph = 2 * np.pi * np.cumsum(f) / SR
    whine = np.sin(ph) + 0.4 * np.sin(2 * ph) + 0.2 * np.sin(3 * ph + 1)
    shimmer = lowpass(rng.standard_normal(len(tt)), 6000) * (tt / tt[-1])
    save("charge", (whine * (0.15 + (tt / tt[-1])) + shimmer * 0.5) * env(len(tt), 0.1, 0.05))

    # beam: a heavy hum with tremolo and hiss
    tt = t(2.6)
    hum = np.sin(2 * np.pi * 55 * tt) + 0.7 * np.sin(2 * np.pi * 110 * tt) + 0.4 * np.sin(2 * np.pi * 220.5 * tt)
    buzz = np.sin(2 * np.pi * 440 * tt + 3 * np.sin(2 * np.pi * 7 * tt)) * 0.25
    hiss = lowpass(rng.standard_normal(len(tt)), 5000) * 0.3
    save("beam", (hum + buzz + hiss) * (0.8 + 0.2 * np.sin(2 * np.pi * 9 * tt)) * env(len(tt), 0.15, 0.6))

    # boom: filtered noise burst + falling sine + long rumble
    tt = t(4.5)
    burst = lowpass(rng.standard_normal(len(tt)), 900) * np.exp(-tt * 2.2)
    drop = np.sin(2 * np.pi * np.cumsum(25 + 70 * np.exp(-tt * 3)) / SR) * np.exp(-tt * 1.1)
    rumble = lowpass(rng.standard_normal(len(tt)), 120) * np.exp(-tt * 0.9) * 1.5
    crack = rng.standard_normal(len(tt)) * np.exp(-tt * 60) * 0.7
    save("boom", (burst * 1.4 + drop * 1.6 + rumble + crack) * env(len(tt), 0.002, 0.4))

    # morph: glassy shattering and a metallic descending cluster
    tt = t(1.6)
    shards = np.zeros(len(tt))
    for _ in range(26):
        s = int(rng.uniform(0, 0.9) * SR)
        n = int(rng.uniform(0.04, 0.2) * SR)
        f0 = rng.uniform(1800, 5200)
        local = np.arange(n) / SR
        seg = np.sin(2 * np.pi * f0 * local) * np.exp(-local * 24)
        room = len(shards[s: s + n])
        shards[s: s + n] += seg[:room] * rng.uniform(0.2, 0.6)
    fm = np.sin(2 * np.pi * np.cumsum(900 * np.exp(-tt * 1.4)) / SR + 2 * np.sin(2 * np.pi * 330 * tt)) * np.exp(-tt * 2) * 0.5
    save("morph", (shards + fm) * env(len(tt), 0.01, 0.3))

    # rail: sharp crack, then a falling ring
    tt = t(2.0)
    crack = rng.standard_normal(len(tt)) * np.exp(-tt * 45)
    sweep = np.sin(2 * np.pi * np.cumsum(120 + 1700 * np.exp(-tt * 6)) / SR) * np.exp(-tt * 3)
    ring = np.sin(2 * np.pi * 523 * tt) * np.exp(-tt * 4) * 0.4 + np.sin(2 * np.pi * 1318 * tt) * np.exp(-tt * 5) * 0.2
    low = np.sin(2 * np.pi * 48 * tt) * np.exp(-tt * 4) * 1.2
    save("rail", (crack * 1.2 + sweep + ring + low) * env(len(tt), 0.001, 0.2))

    # shield: a short hexagonal ping
    tt = t(0.7)
    ping = np.sin(2 * np.pi * 1180 * tt) * np.exp(-tt * 9) + 0.6 * np.sin(2 * np.pi * 1770 * tt) * np.exp(-tt * 12)
    tick = rng.standard_normal(len(tt)) * np.exp(-tt * 70) * 0.5
    save("shield", (ping + tick) * env(len(tt), 0.001, 0.1))


if __name__ == "__main__":
    crystal()
    earth()
    item_icons()
    sounds()
    print("assets written to", os.path.abspath(ROOT))
