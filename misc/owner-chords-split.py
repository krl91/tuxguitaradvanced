#!/usr/bin/env python3
"""Splits the owner's chord recordings into takes (spec 024, owner-chords-3).

Reads chord-NN.wav (one file per chord, several strums each, recorded with sox from the Rocksmith adapter) in a folder, finds each strum (a fast rise
above -45 dBFS that stays above -50 dBFS for 0.6 s), writes one take per strum (0.15 s before it, at most 4.4 s, 48 kHz 16-bit) and prints a chroma
template guess of each take's chord. Tool: librosa (pip install librosa soundfile), in a separate virtual environment.
Usage: python3 misc/owner-chords-split.py <recordings> <takes folder>
"""
import sys, glob, os, warnings
warnings.filterwarnings('ignore')
import numpy as np, librosa, soundfile as sf
src, dst = sys.argv[1], sys.argv[2]
os.makedirs(dst, exist_ok=True)
NAMES = ['C','C#','D','D#','E','F','F#','G','G#','A','A#','B']
QUAL = {'':[0,4,7], 'm':[0,3,7], '7':[0,4,7,10], 'm7':[0,3,7,10], 'maj7':[0,4,7,11], 'sus2':[0,2,7], 'sus4':[0,5,7], '5':[0,7]}
templates = []
for r in range(12):
    for q, iv in QUAL.items():
        t = np.zeros(12); t[[(r+i)%12 for i in iv]] = 1
        templates.append((NAMES[r]+q, t/np.linalg.norm(t)))
rows = []; n = 0
for f in sorted(glob.glob(src+'/chord-*.wav')):
    y, sr = librosa.load(f, sr=48000, mono=True)
    h = 480
    db = 20*np.log10(librosa.feature.rms(y=y, frame_length=1024, hop_length=h)[0]+1e-9)
    keep = []
    for i in range(len(db)-60):
        rise = i < 5 and db[i] > -45 or i >= 5 and db[i]-db[i-5:i].min() > 15
        # a strum: a fast rise above -45 dBFS that stays above -50 dBFS for 0.6 s (a click or a mute does not)
        if rise and db[i] > -45 and db[i:i+60].min() > -50 and (not keep or i*h-keep[-1] > 1.5*sr):
            keep.append(i*h)
    for i, o in enumerate(keep):
        end = keep[i+1] if i+1 < len(keep) else len(y)
        a = max(0, o-int(0.15*sr)); b = min(end-int(0.05*sr), a+int(4.4*sr))
        seg = y[a:b]
        n += 1; name = 'take%02d.wav' % n
        sf.write(os.path.join(dst, name), seg, sr, subtype='PCM_16')
        # chroma over 0.1..1.5 s after the strum
        s2 = seg[int(0.25*sr):int(1.65*sr)]
        C = librosa.feature.chroma_cqt(y=s2, sr=sr, hop_length=512, fmin=librosa.note_to_hz('E2'), n_octaves=5)
        ch = C.mean(axis=1); chn = ch/np.linalg.norm(ch)
        sc = sorted(((float(chn@t), nm) for nm, t in templates), reverse=True)
        top = ' '.join(NAMES[i] for i in np.argsort(ch)[::-1][:5])
        rows.append((name, os.path.basename(f), sc[0][1], round(sc[0][0],3), sc[1][1], round(sc[1][0],3), top))
for r in rows: print(*r, sep='\t')
