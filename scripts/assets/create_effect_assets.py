#!/usr/bin/env python3
"""Original deterministic particles, burned metal, covers and short spatial rocket sounds.

No source recording, imported model, SBW texture or shader-pack resource is used.
Requires Pillow and ffmpeg/libvorbis. Reload audio remains in create_vehicle_sounds.py.
"""
import array
import json
import math
from pathlib import Path
import random
import subprocess
import sys
import tempfile
import wave
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2] / 'src/main/resources/assets/iruxfk2000'
RATE = 44100


def noise_image(size, seed):
    rng = random.Random(seed)
    layers = []
    for resolution in (8, 16, 32, 64):
        image = Image.new('L', (resolution, resolution))
        image.putdata([rng.randrange(256) for _ in range(resolution * resolution)])
        layers.append(image.resize((size, size), Image.Resampling.BICUBIC))
    return [[sum(layer.getpixel((x, y)) * weight for layer, weight in zip(layers, (.5, .25, .15, .1))) / 255
             for x in range(size)] for y in range(size)]


def textures():
    particle = ROOT / 'textures/particle'; entity = ROOT / 'textures/entity'
    particle.mkdir(parents=True, exist_ok=True); entity.mkdir(parents=True, exist_ok=True)
    for frame in range(4):
        noise = noise_image(128, 20400 + frame)
        image = Image.new('RGBA', (128, 128)); pixels = image.load()
        for y in range(128):
            for x in range(128):
                nx, ny = (x - 63.5) / 57, (y - 63.5) / 57
                radius = math.sqrt(nx * nx + ny * ny)
                wisps = noise[y][x]
                density = max(0, 1 - radius + (wisps - .5) * .5)
                alpha = round(255 * min(1, density * 1.6) ** 1.35 * (.35 + wisps * .65))
                pixels[x, y] = (255, 255, 255, alpha)
        image.save(particle / f'exhaust_smoke_{frame}.png')
    ring = Image.new('RGBA', (256, 256)); pixels = ring.load()
    for y in range(256):
        for x in range(256):
            radius = math.hypot(x - 127.5, y - 127.5) / 127.5
            pressure = math.exp(-((radius - .78) / .023) ** 2)
            pressure += .18 * math.exp(-((radius - .70) / .045) ** 2)
            pixels[x, y] = (230, 242, 255, round(min(1, pressure) * 210))
    ring.save(particle / 'shockwave.png')
    noise = noise_image(128, 20455)
    metal = Image.new('RGBA', (128, 128)); pixels = metal.load()
    for y in range(128):
        for x in range(128):
            crack = abs(math.sin(x * .13 + math.sin(y * .08) * 2)) < .035
            shade = round(12 + noise[y][x] * 30) - (8 if crack else 0)
            pixels[x, y] = (shade, shade, shade, 255)
    metal.save(entity / 'charred-metal.png')
    cap = Image.new('RGBA', (128, 128), (65, 79, 47, 255)); draw = ImageDraw.Draw(cap)
    draw.ellipse((4, 4, 123, 123), fill=(92, 101, 69), outline=(30, 34, 26), width=5)
    draw.ellipse((12, 12, 115, 115), fill=(57, 72, 40), outline=(113, 119, 80), width=2)
    for angle in (0, 2 * math.pi / 3, 4 * math.pi / 3):
        x = 64 + math.cos(angle) * 43; y = 64 + math.sin(angle) * 43
        draw.ellipse((x - 4, y - 4, x + 4, y + 4), fill=(152, 147, 112), outline=(29, 31, 23))
        draw.line((x - 2, y, x + 2, y), fill=(38, 40, 29))
    draw.ellipse((51, 51, 76, 76), fill=(76, 88, 54), outline=(28, 32, 21), width=2)
    cap.save(entity / 'tube-cap.png')
    plume = Image.new('RGBA', (64, 128)); pixels = plume.load()
    for y in range(128):
        t = y / 127
        for x in range(64):
            radial = (x - 31.5) / 31.5
            alpha = math.exp(-radial * radial * 7) * (1 - t) ** .7
            pixels[x, y] = (255, round(252 - t * 100), round(235 - t * 200), round(alpha * 250))
    plume.save(entity / 'rocket-exhaust.png')
    definitions = ROOT / 'particles'; definitions.mkdir(exist_ok=True)
    (definitions / 'exhaust_smoke.json').write_text(json.dumps({'textures': [f'iruxfk2000:exhaust_smoke_{i}' for i in range(4)]}, indent=2) + '\n')
    (definitions / 'shockwave.json').write_text(json.dumps({'textures': ['iruxfk2000:shockwave']}, indent=2) + '\n')


def audio(name, duration, seed, style):
    rng = random.Random(seed); values = []; low = 0; air = 0
    for i in range(round(RATE * duration)):
        t = i / RATE; noise = rng.uniform(-1, 1)
        low += .012 * (noise - low); air += .35 * (noise - air)
        if style == 'loop':
            # Periodic low resonances; steady broadband exhaust has no artificial slow cadence.
            phase = t / duration
            rumble = sum(math.sin(math.tau * cycle * phase + offset) * weight
                         for cycle, offset, weight in ((31, .3, .30), (53, 1.2, .15), (91, 2.1, .06)))
            value = rumble + (air - low) * .4 + low * 1.4
        elif style == 'ignition':
            envelope = min(1, t / .004) * math.exp(-t / .105)
            value = envelope * (math.sin(math.tau * (100 * t - 75 * t * t)) * .7 + air * .9 + low * 1.5)
        else:
            initial = min(1, t / .002) * math.exp(-t / .13)
            tail = math.exp(-t / .27)
            value = initial * (air * 1.3 + math.sin(math.tau * 68 * t) * .55) + low * tail * 2.7
        values.append(value)
    if style == 'loop':
        # Crossfade one short boundary so a repeated source does not click.
        count = round(RATE * .015)
        for i in range(count):
            a = i / count
            mixed = values[-count + i] * (1 - a) + values[i] * a
            values[-count + i] = mixed
    else:
        for i in range(min(512, len(values))): values[-i - 1] *= i / 512
    peak = max(abs(v) for v in values)
    samples = array.array('h', (round(v / peak * .88 * 32767) for v in values))
    if sys.byteorder != 'little': samples.byteswap()
    out = ROOT / 'sounds' / f'{name}.ogg'; out.parent.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='fk2000-effects-') as directory:
        wav_path = Path(directory) / 'effect.wav'
        with wave.open(str(wav_path), 'wb') as wav:
            wav.setnchannels(1); wav.setsampwidth(2); wav.setframerate(RATE); wav.writeframes(samples.tobytes())
        subprocess.run(['ffmpeg', '-nostdin', '-hide_banner', '-loglevel', 'error', '-y', '-i', str(wav_path),
                        '-map_metadata', '-1', '-fflags', '+bitexact', '-flags:a', '+bitexact',
                        '-c:a', 'libvorbis', '-q:a', '6', str(out)], check=True)


def main():
    textures()
    audio('missile_ignition', .42, 20461, 'ignition')
    audio('missile_flight', .64, 20462, 'loop')
    audio('missile_explosion', 1.05, 20463, 'explosion')
    sounds_path = ROOT / 'sounds.json'; sounds = json.loads(sounds_path.read_text())
    for event, distance in (('missile_ignition', 160), ('missile_flight', 128), ('missile_explosion', 192)):
        sounds[event] = {'subtitle': f'subtitles.iruxfk2000.{event}', 'sounds': [
            {'name': f'iruxfk2000:{event}', 'attenuation_distance': distance, 'volume': 1.0}]}
    sounds_path.write_text(json.dumps(sounds, indent=2) + '\n')
    for language, labels in {
        'fr_fr': ('Allumage du missile', 'Souffle du réacteur du missile', 'Explosion de l’ogive'),
        'en_us': ('Missile engine ignites', 'Missile engine roars', 'Warhead explodes')}.items():
        path = ROOT / 'lang' / f'{language}.json'; data = json.loads(path.read_text())
        data.update({f'subtitles.iruxfk2000.{event}': label for event, label in zip(
            ('missile_ignition', 'missile_flight', 'missile_explosion'), labels)})
        path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n')
    print('Created original smoke, pressure ring, exhaust, burned metal, covers and three short mono OGG sounds.')


if __name__ == '__main__': main()
