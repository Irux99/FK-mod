#!/usr/bin/env python3
"""Synthesize an original 0.62-second mechanical reload, without source recordings.

Requires Python 3 and ffmpeg with libvorbis. Run from any directory; optional
first argument selects the output OGG. The fixed PRNG seed and bit-exact encoder
options make the same PCM and OGG reproducible with the same ffmpeg build.
"""

import argparse
import array
import math
from pathlib import Path
import random
import subprocess
import sys
import tempfile
import wave


RATE = 44100
DURATION = 0.62


def synthesize():
    """Latch opens, carriage slides, heavy detent seats, safety catch clicks."""
    samples = [0.0] * round(RATE * DURATION)
    rng = random.Random(200003)

    def strike(start, strength, duration, pitch):
        for i in range(round(duration * RATE)):
            t = i / RATE
            index = round(start * RATE) + i
            if index >= len(samples):
                break
            attack = min(1.0, t / 0.0015)
            resonances = sum(
                weight * math.sin(2 * math.pi * pitch * ratio * t) * math.exp(-t / decay)
                for ratio, weight, decay in ((1, .55, .035), (2.31, .28, .026),
                                             (4.73, .14, .018), (7.11, .08, .012))
            )
            noise = rng.uniform(-1, 1) * math.exp(-t / .008) * .32
            thunk = math.sin(2 * math.pi * 86 * t) * math.exp(-t / .028) * .38
            samples[index] += strength * attack * (resonances + noise + thunk)

    strike(.015, .72, .12, 265)
    # A short, band-emphasized hydraulic slide with a falling motor pitch.
    previous = 0.0
    for i in range(round(.30 * RATE)):
        t = i / RATE
        index = round(.07 * RATE) + i
        envelope = math.sin(math.pi * t / .30) ** .7
        white = rng.uniform(-1, 1)
        hiss = (white - previous) * .045
        previous = white
        whirr = math.sin(2 * math.pi * (210 * t - 110 * t * t)) * .12
        samples[index] += envelope * (hiss + whirr)
    for start, strength, pitch in ((.15, .12, 430), (.22, .15, 405), (.29, .12, 370)):
        strike(start, strength, .065, pitch)
    strike(.385, 1.0, .17, 190)
    strike(.505, .40, .095, 480)
    peak = max(abs(sample) for sample in samples)
    pcm = array.array('h', (round(sample / peak * 0.88 * 32767) for sample in samples))
    if sys.byteorder != 'little':
        pcm.byteswap()
    return pcm.tobytes()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('output', nargs='?', type=Path, default=
                        Path(__file__).resolve().parents[2] /
                        'src/main/resources/assets/iruxfk2000/sounds/reload.ogg')
    output = parser.parse_args().output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='fk2000-sound-') as temporary:
        source = Path(temporary) / 'reload.wav'
        with wave.open(str(source), 'wb') as wav:
            wav.setnchannels(1)
            wav.setsampwidth(2)
            wav.setframerate(RATE)
            wav.writeframes(synthesize())
        subprocess.run(['ffmpeg', '-nostdin', '-hide_banner', '-loglevel', 'error', '-y',
                        '-i', str(source), '-map_metadata', '-1', '-fflags', '+bitexact',
                        '-flags:a', '+bitexact', '-c:a', 'libvorbis', '-q:a', '6', str(output)], check=True)
    print(f'Created original mono reload: {output} ({DURATION:.2f} s, {RATE} Hz)')


if __name__ == '__main__':
    main()
