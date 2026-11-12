#!/usr/bin/env python3
"""Regenerate the short UI sounds in app/src/main/res/raw. Standard library only."""
import math
import random
import struct
import wave
from pathlib import Path

RATE = 44100
OUT = Path(__file__).resolve().parent.parent / "app/src/main/res/raw"


def write(name: str, samples: list[float]) -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    with wave.open(str(OUT / f"{name}.wav"), "wb") as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(RATE)
        wav.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, s)) * 32767)) for s in samples))


def sweep(start: float, end: float, seconds: float, decay: float, gain: float) -> list[float]:
    count = int(RATE * seconds)
    out, phase = [], 0.0
    for i in range(count):
        t = i / count
        freq = start + (end - start) * t
        phase += 2 * math.pi * freq / RATE
        out.append(gain * math.sin(phase) * math.exp(-decay * i / RATE))
    return out


def click(seconds: float = 0.045) -> list[float]:
    rng = random.Random(7)
    body = sweep(1700, 1100, seconds, 90, .30)
    transient = [(rng.random() * 2 - 1) * math.exp(-i / 60) * .25 for i in range(int(RATE * .006))]
    return [a + (transient[i] if i < len(transient) else 0) for i, a in enumerate(body)]


def chime() -> list[float]:
    first = sweep(880, 880, .13, 22, .26)
    second = sweep(1320, 1320, .22, 16, .26)
    return first + [0.0] * int(RATE * .02) + second


write("ui_tap", click())
write("ui_on", sweep(600, 1400, .11, 26, .30))
write("ui_off", sweep(1200, 520, .11, 26, .30))
write("ui_success", chime())
