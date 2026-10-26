#!/usr/bin/env python3
"""NightCrush audio, synthesized offline: short cues plus ambient loops.

Everything is generated from sine/noise primitives and encoded to OGG with
ffmpeg, so the repository ships no third-party audio and the APK stays small.
"""
from pathlib import Path
import math
import random
import struct
import subprocess
import wave

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "app/src/main/res/raw"
RATE = 22050
MUSIC_RATE = 16000

# Pentatonic ladder: one step per cascade.
PENTATONIC = [523.25, 587.33, 659.25, 783.99, 880.0, 1046.5, 1174.66, 1318.51, 1567.98, 1760.0]


def envelope(i, count, attack=0.01, curve=1.8):
    attack_samples = max(1, int(RATE * attack))
    a = min(1.0, i / attack_samples)
    d = max(0.0, 1.0 - i / count) ** curve
    return a * d


def tone(freq, duration, harmonics=(1.0, 0.18, 0.06), curve=1.8, attack=0.01):
    count = int(RATE * duration)
    out = []
    for i in range(count):
        t = i / RATE
        value = sum(weight * math.sin(2 * math.pi * freq * mult * t)
                    for mult, weight in zip((1, 2, 3), harmonics))
        out.append(value * envelope(i, count, attack, curve) * 6500)
    return out


def slide(start_freq, end_freq, duration, curve=1.6):
    count = int(RATE * duration)
    out = []
    phase = 0.0
    for i in range(count):
        freq = start_freq + (end_freq - start_freq) * (i / count)
        phase += 2 * math.pi * freq / RATE
        out.append(math.sin(phase) * envelope(i, count, 0.005, curve) * 6500)
    return out


def noise(duration, low=0.0, high=1.0, curve=2.0):
    count = int(RATE * duration)
    rng = random.Random(7)
    out = []
    smoothed = 0.0
    for i in range(count):
        raw = rng.uniform(-1.0, 1.0)
        smoothed = smoothed * (1 - high) + raw * high
        value = smoothed * (1 - low) + raw * low
        out.append(value * envelope(i, count, 0.004, curve) * 5200)
    return out


def mix(*tracks):
    length = max(len(t) for t in tracks)
    out = [0.0] * length
    for track in tracks:
        for i, value in enumerate(track):
            out[i] += value
    return [max(-32000, min(32000, v)) for v in out]


def chain(*tracks):
    return [v for track in tracks for v in track]


def write_wav(path, samples, rate=RATE):
    path.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(path), "wb") as audio:
        audio.setnchannels(1)
        audio.setsampwidth(2)
        audio.setframerate(rate)
        audio.writeframes(struct.pack("<" + "h" * len(samples), *[int(s) for s in samples]))


def write_ogg(path, samples, rate=RATE):
    wav = path.with_suffix(".wav")
    write_wav(wav, samples, rate)
    subprocess.run(
        ["ffmpeg", "-y", "-loglevel", "error", "-i", str(wav), "-c:a", "libvorbis",
         "-b:a", "64k", "-ar", str(rate), "-ac", "1", str(path)],
        check=True,
    )
    wav.unlink()


def sfx():
    sounds = {
        "click": chain(tone(880, 0.05, curve=3.0)),
        "reject": chain(slide(420, 260, 0.18, 2.2)),
        "swap": chain(tone(660, 0.06, curve=2.4), tone(990, 0.05, curve=2.6)),
        "special_ray": chain(noise(0.28, low=0.1, high=0.9, curve=2.0), slide(1200, 300, 0.28, 2.0)),
        "special_jelly": chain(tone(196, 0.22, curve=1.4), tone(147, 0.26, curve=1.6)),
        "special_fish": chain(*[tone(700 + 120 * k, 0.05, curve=3.0) for k in range(4)]),
        "special_pearl": mix(
            tone(659.25, 0.5, curve=1.2),
            tone(830.61, 0.5, curve=1.2),
            tone(987.77, 0.5, curve=1.2),
        ),
        "combo": mix(tone(523.25, 0.4, curve=1.4), tone(783.99, 0.4, curve=1.4), noise(0.35, low=0.05, high=0.7, curve=2.2)),
        "layer_break": chain(noise(0.16, low=0.5, high=1.0, curve=2.6)),
        "lantern": chain(tone(987.77, 0.12, curve=2.0), tone(1318.51, 0.2, curve=1.8)),
        "seahorse": chain(*[tone(587.33 * (1 + 0.12 * k), 0.09, curve=2.4) for k in range(4)]),
        "puffer_tick": chain(tone(1200, 0.03, curve=4.0)),
        "puffer_boom": mix(slide(220, 60, 0.5, 1.4), noise(0.5, low=0.2, high=0.8, curve=2.0)),
        "oil_spread": chain(slide(300, 90, 0.4, 1.6)),
        "star1": chain(tone(783.99, 0.16, curve=2.0)),
        "star2": chain(tone(783.99, 0.14, curve=2.0), tone(987.77, 0.2, curve=1.8)),
        "star3": chain(tone(783.99, 0.12, curve=2.0), tone(987.77, 0.14, curve=1.8), tone(1318.51, 0.28, curve=1.5)),
        "win": chain(*[tone(f, 0.16 if k < 4 else 0.4, curve=1.6) for k, f in enumerate([523.25, 659.25, 783.99, 1046.5, 1318.51])]),
        "lose": chain(tone(392, 0.2, curve=1.8), tone(311.13, 0.36, curve=1.6)),
        "chest": chain(*[tone(659.25 + 90 * k, 0.07, curve=2.6) for k in range(5)], tone(1318.51, 0.3, curve=1.5)),
        "sticker": chain(tone(1046.5, 0.12, curve=2.2), tone(1567.98, 0.18, curve=1.8)),
    }
    for k, freq in enumerate(PENTATONIC):
        sounds["match%d" % (k + 1)] = chain(tone(freq, 0.16, curve=2.2), tone(freq * 2, 0.1, curve=2.6))
    for name, samples in sounds.items():
        write_ogg(OUT / (name + ".ogg"), samples)
    print("sfx: %d sounds" % len(sounds))


def music_loop(index, name, brightness, seconds=64):
    """A soft pad, filtered water and occasional glass bells."""
    rng = random.Random(1000 + index)
    total = MUSIC_RATE * seconds
    base_notes = [
        [110.0, 164.81, 220.0, 261.63],
        [98.0, 146.83, 196.0, 246.94],
        [123.47, 185.0, 246.94, 293.66],
        [103.83, 155.56, 207.65, 261.63],
        [116.54, 174.61, 233.08, 277.18],
        [87.31, 130.81, 174.61, 220.0],
        [130.81, 196.0, 261.63, 311.13],
    ][index % 7]
    chord_length = seconds / 4
    samples = [0.0] * (MUSIC_RATE * seconds)
    for segment in range(4):
        start = int(segment * chord_length * MUSIC_RATE)
        length = int(chord_length * MUSIC_RATE)
        for note_index, freq in enumerate(base_notes):
            detune = 1.0 + (note_index - 1.5) * 0.0008
            volume = (0.16 - note_index * 0.02) * brightness
            for i in range(length):
                t = i / MUSIC_RATE
                fade = min(1.0, i / (MUSIC_RATE * 2.0)) * min(1.0, (length - i) / (MUSIC_RATE * 2.0))
                samples[start + i] += (
                    math.sin(2 * math.pi * freq * detune * t)
                    + 0.4 * math.sin(2 * math.pi * freq * 2.0 * t)
                ) * volume * fade
    # Water: heavily smoothed noise.
    smoothed = 0.0
    for i in range(len(samples)):
        smoothed = smoothed * 0.9995 + rng.uniform(-1, 1) * 0.0005
        samples[i] += smoothed * 6.0
    # Glass bells, sparse.
    for _ in range(14):
        pos = rng.randint(0, len(samples) - MUSIC_RATE)
        freq = 880 * (1 + rng.random())
        for i in range(int(MUSIC_RATE * 1.6)):
            t = i / MUSIC_RATE
            samples[pos + i] += math.sin(2 * math.pi * freq * t) * (1 - i / (MUSIC_RATE * 1.6)) ** 2.5 * 900
    # Slow fade in and out so loops cross well.
    fade = MUSIC_RATE * 2
    for i in range(fade):
        samples[i] *= i / fade
        samples[-i - 1] *= i / fade
    write_ogg(OUT / (name + ".ogg"), samples, MUSIC_RATE)
    print("%s: %.1f s" % (name, seconds))


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    sfx()
    for i in range(1, 7):
        music_loop(i, "music_%d" % i, brightness=0.7 + 0.05 * i, seconds=64)
    music_loop(7, "music_zen", brightness=0.55, seconds=72)
    total = sum(p.stat().st_size for p in OUT.glob("*.ogg"))
    print("total audio: %.2f MB" % (total / 1_000_000))


if __name__ == "__main__":
    main()
