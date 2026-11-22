"""Synthesises the game's short sounds (no third-party audio, so no licence to credit).
word.wav: two soft bell notes for a found word; level.wav: a rising three-note chime for a solved level."""
import math, struct, wave, os
RATE = 22050
def bell(freq, start, dur, vol, out):
    for i in range(int(dur * RATE)):
        t = i / RATE
        env = min(1.0, t / 0.008) * math.exp(-t * 6.0)
        v = math.sin(2 * math.pi * freq * t) + 0.35 * math.sin(2 * math.pi * freq * 2 * t) + 0.12 * math.sin(2 * math.pi * freq * 3 * t)
        j = int(start * RATE) + i
        out[j] += vol * env * v / 1.47
def write(name, notes, length):
    buf = [0.0] * int(length * RATE)
    for f, s, d, v in notes: bell(f, s, d, v, buf)
    path = os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main', 'res', 'raw', name)
    with wave.open(path, 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(RATE)
        w.writeframes(b''.join(struct.pack('<h', int(max(-1, min(1, x)) * 30000)) for x in buf))
write('word.wav', [(880.0, 0.0, 0.35, 0.45), (1318.5, 0.09, 0.4, 0.4)], 0.5)
write('level.wav', [(659.3, 0.0, 0.5, 0.4), (880.0, 0.13, 0.5, 0.4), (1318.5, 0.26, 0.8, 0.45)], 1.1)
