#!/usr/bin/env python3
"""Labels of the owner's chord takes (spec 004 T007, spec 010 T005).

Writes labels.csv next to the WAV files of a folder: for each take, the MIDI notes that a polyphonic transcription hears in the first 0.8 s after the first
onset (amplitude above 0.3, MIDI 40 to 69), without the partials a lower note of the set explains (19, 28 or 31 semitones above it).
Tool: Spotify Basic Pitch (pip install basic-pitch onnxruntime; the version used on 2026-10-14 is the one pip gave then, record it in the manifest).
The labels come from a tool and not from the owner: they are evidence, not a verified ground truth.
Usage: python3 misc/owner-chords-labels.py specs/004-attack-difference-recognition/benchmark/owner-chords
"""
import sys,glob,os,warnings
warnings.filterwarnings('ignore')
from basic_pitch.inference import predict
d=sys.argv[1]
rows=[]
for f in sorted(glob.glob(d+'/take*.wav')):
    mo,md,ne=predict(f)
    ne=sorted(ne,key=lambda n:n[0])
    if not ne: rows.append((os.path.basename(f),[])); continue
    t0=ne[0][0]
    best={}
    for n in ne:
        if n[0]<t0+0.8 and n[3]>0.3 and 40<=n[2]<=69: best[n[2]]=max(best.get(n[2],0),n[3])
    keep=sorted(best)
    # a partial that a lower note of the set explains (12th, 17th, 19th above it) is not a played note
    keep=[m for m in keep if not any(m-l in (19,28,31) for l in keep if l<m)]
    rows.append((os.path.basename(f),keep))
with open(d+'/labels.csv','w') as o:
    o.write('file,midi\n')
    for f,m in rows: o.write('%s,%s\n'%(f,' '.join(map(str,m))))
print(open(d+'/labels.csv').read())
