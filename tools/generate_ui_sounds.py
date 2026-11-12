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


def click(seed: int, seconds: float, body_hz: float, gain: float) -> list[float]:
    """Dry mechanical impulse: high-passed noise + rapidly damped fixed resonances.

    No pitch sweep, chime or reverb. A tiny attack and zero-ended tail avoid a DC pop.
    """
    rng = random.Random(seed)
    samples, previous = [], 0.0
    count = int(RATE * seconds)
    for i in range(count):
        t = i / RATE
        noise = rng.uniform(-1, 1)
        edge = (noise - previous) * .5
        previous = noise
        attack = min(1.0, i / (RATE * .0002))
        tail = (1 - i / (count - 1)) ** 2
        transient = edge * math.exp(-t / .0016) * .8
        body = math.sin(2 * math.pi * body_hz * t) * math.exp(-t / .0028) * .35
        snap = math.sin(2 * math.pi * 3200 * t) * math.exp(-t / .0007) * .22
        samples.append((transient + body + snap) * attack * tail)
    peak = max(abs(s) for s in samples)
    return [s * gain / peak for s in samples]


write("ui_tap", click(7, .018, 900, .75))
write("ui_on", click(11, .022, 720, .78))
write("ui_off", click(13, .020, 650, .72))
write("ui_success", click(17, .024, 820, .78))
write("ui_tick", click(23, .009, 1500, .50))
write("ui_detent", click(29, .014, 1050, .65))
