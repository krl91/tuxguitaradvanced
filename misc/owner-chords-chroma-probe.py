#!/usr/bin/env python3
"""Offline probe of chord recognition by pitch-class profile (spec 024 plan).

For each take of a folder with labels.csv, the chroma of the first second after the strum is compared (cosine) with the template of every take's chord:
its own (right), the same note names (voicing), one name off, or two or more names missing (clearly wrong); prints how many of each a threshold accepts.
Tool: librosa (pip install librosa). It is evidence for the plan, not the product's method.
Usage: python3 misc/owner-chords-chroma-probe.py specs/004-attack-difference-recognition/benchmark/owner-chords-3
"""
import sys, csv, warnings
warnings.filterwarnings('ignore')
import numpy as np, librosa
d = sys.argv[1]
rows = list(csv.DictReader(open(d+'/labels.csv')))
takes = [(r['file'], [int(x) for x in r['midi'].split()]) for r in rows]
def pcs(m): return frozenset(x % 12 for x in m)
chroma = {}
for f, _ in takes:
    y, sr = librosa.load(d+'/'+f, sr=48000)
    s = y[int(0.2*sr):int(1.2*sr)]  # the first second after the strum
    C = librosa.feature.chroma_cqt(y=s, sr=sr, hop_length=512, fmin=librosa.note_to_hz('E2'), n_octaves=5)
    c = C.mean(axis=1); chroma[f] = c/np.linalg.norm(c)
def score(c, names):
    t = np.zeros(12); t[list(names)] = 1; return float(c @ (t/np.linalg.norm(t)))
pairs = []
for f, m in takes:
    for g, n in takes:
        a, b = pcs(m), pcs(n)
        missing = len(b - a)
        kind = 'right' if g == f else ('voicing' if a <= b or b <= a else ('one-off' if missing <= 1 else 'clearly-wrong'))
        if kind == 'right' or g != f and kind != 'right':
            pairs.append((kind, score(chroma[f], b), f, sorted(b)))
pairs = [p for p in pairs if not (p[0]=='voicing' and p[2]==p[2] and False)]
right = sorted(p[1] for p in pairs if p[0]=='right')
print('right scores min %.3f, median %.3f' % (right[0], right[len(right)//2]))
for th in [right[0]-0.001, 0.85, 0.80, 0.75]:
    cnt = {k: (sum(1 for p in pairs if p[0]==k and p[1]>=th), sum(1 for p in pairs if p[0]==k)) for k in ['right','voicing','one-off','clearly-wrong']}
    print('threshold %.3f: ' % th + ', '.join('%s %d/%d' % (k, a, b) for k, (a, b) in cnt.items()))
cw = sorted((p for p in pairs if p[0]=='clearly-wrong'), key=lambda p: -p[1])[:5]
print('highest clearly wrong:', [(p[2], p[3], round(p[1],3)) for p in cw])
