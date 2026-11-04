# Arcade stress run, 600 s per run (spec 003 T047)

plan: plain,render; notes/s 3 (chords on every other beat)

Machine: Mac OS X aarch64, Java 21.0.13, 11 processors. Window 1280x720, SWT painter, quality controller on.

### run 1: without the renderer
- run ended: PLAYER_STOPPED; events 1127, score 133030 of 223650 (59 %), best streak 64
- judgement: single notes matched 797, offset from the written time (negative = early) min -95, p5 -14, p50 7, p95 18, max 28 ms; chords matched 209, offset from the written time (negative = early) min -176, p5 -25, p50 -15, p95 42, max 56 ms
- audio: capture overruns (samples refused by the ring buffer) 0; analysis frames 84681, longest gap between two frames 27 ms; the feeder was at most 4 ms late for a block
- MIDI: backing notes delivered to the silent channels 563 of 563 expected; longest time the player's position did not move while it ran: 18 ms
- JVM: 11 collections, 31 ms in total

### run 2: with the renderer
- run ended: PLAYER_STOPPED; events 1127, score 132990 of 223650 (59 %), best streak 64
- judgement: single notes matched 797, offset from the written time (negative = early) min -114, p5 -33, p50 -9, p95 -1, max 7 ms; chords matched 209, offset from the written time (negative = early) min -191, p5 -45, p50 -30, p95 28, max 34 ms
- audio: capture overruns (samples refused by the ring buffer) 0; analysis frames 84965, longest gap between two frames 23 ms; the feeder was at most 0 ms late for a block
- MIDI: backing notes delivered to the silent channels 563 of 563 expected; longest time the player's position did not move while it ran: 15 ms
- JVM: 202 collections, 133 ms in total
- frames: 54185 frames painted in 27101 deadline slots of 16.7 ms (timer ticks skipped because a frame was pending: 54220, which is not a miss); paint cost p50 3.77 p95 4.06 p99 4.19 max 14.41 ms; interval between paints p50 8.33 p95 9.05 p99 9.35 ms; missed deadline slots 2, longest run of consecutive misses 2 (began 0.0 s into the run); most tasks waiting in the UI thread's queue at once 0; requests the scheduler took as lost 0, ticks it skipped 54417; allocation per paint p50 571 p99 673 KB; paints longer than two frames: none; gaps of more than 50 ms between two paints: [66 ms at 0.1 s]
- what the UI thread was doing in each gap of more than 40 ms between two paints:
    gap 66 ms at 0.1 s, 0 samples of the UI thread:
- renderer path: toolkit painter
- quality tier at the end: FULL, 0 changes during the run

### digest of the judgements
- run 1 (plain) against run 2 (render): 2 differing lines of 1129
  run 1 125|MATCHED|ON_TIME|[] exp=[]   <>   run 2 125|MATCHED|EARLY|[] exp=[]
  run 1 score=133030 max=223650 best=64 percent=59   <>   run 2 score=132990 max=223650 best=64 percent=59
