package app.tuxguitar.app.tools.custom.practice.arcade.corpus;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import app.tuxguitar.app.tools.custom.practice.arcade.view.ArcadeRenderEvent;
import app.tuxguitar.app.tools.custom.practice.model.PracticeEvent;
import app.tuxguitar.app.tools.custom.practice.model.PracticeSequence;
import app.tuxguitar.app.tools.custom.practice.session.FollowTimeline;
import app.tuxguitar.song.models.TGMeasureHeader;
import app.tuxguitar.song.models.TGSong;
import app.tuxguitar.song.models.TGTrack;

/**
 * What a playable track of a real score looks like, for choosing the cases of the validation (spec 003 FR-064, T060): measured on the events the application
 * builds for it ({@link PracticeSequence}, {@link FollowTimeline}), never on the raw notes of a beat. A chord is an event of kind {@code EXPECTED_CHORD}.
 */
public final class TrackMetrics {

	public int trackNumber;
	public int events;
	public int notes;
	public int chords;
	/** Events that are neither a note nor a chord of the view (passed automatically or not supported). */
	public int others;
	public int longestChordRun;
	public double peakPerSecond;
	public int namedChords;
	public int unnamedChords;
	public int maxChordSize;
	public int minFret;
	public int maxFret;
	public int maxJump;
	public int strings;
	public int tracks;
	public int repeats;
	public int tempoChanges;
	public double durationSeconds;
	public boolean sequenceMonotonic = true;

	public double chordRatio() {
		return this.notes + this.chords == 0 ? 0.0 : (double) this.chords / (this.notes + this.chords);
	}

	/** The metrics of a track from what the application builds for it. */
	public static TrackMetrics of(TGSong song, TGTrack track, PracticeSequence sequence, FollowTimeline timeline) {
		TrackMetrics m = new TrackMetrics();
		m.trackNumber = track.getNumber();
		m.strings = track.stringCount();
		m.tracks = song.countTracks();
		List<PracticeEvent> practice = sequence.getEvents();
		List<ArcadeRenderEvent> events = new ArrayList<ArcadeRenderEvent>();
		for (int i = 0; i < practice.size(); i++) {
			events.add(ArcadeRenderEvent.of(i, practice.get(i), timeline));
		}
		m.events = events.size();
		int run = 0;
		int minFret = Integer.MAX_VALUE;
		int maxFret = 0;
		double previousCentre = -1.0;
		int maxJump = 0;
		long previousOnset = Long.MIN_VALUE;
		for (ArcadeRenderEvent event : events) {
			if (event.onsetNanos() < previousOnset) {
				m.sequenceMonotonic = false;
			}
			previousOnset = event.onsetNanos();
			switch (event.kind()) {
				case NOTE:
					m.notes++;
					run = 0;
					break;
				case CHORD:
					m.chords++;
					run++;
					m.longestChordRun = Math.max(m.longestChordRun, run);
					m.maxChordSize = Math.max(m.maxChordSize, event.positions().size());
					if (event.chordName() != null) {
						m.namedChords++;
					} else {
						m.unnamedChords++;
					}
					break;
				default:
					m.others++;
					run = 0;
					break;
			}
			double sum = 0.0;
			int fretted = 0;
			for (ArcadeRenderEvent.Position position : event.positions()) {
				if (position.fret() > 0) {
					sum += position.fret();
					fretted++;
					minFret = Math.min(minFret, position.fret());
					maxFret = Math.max(maxFret, position.fret());
				}
			}
			if (fretted > 0) {
				double centre = sum / fretted;
				if (previousCentre >= 0.0) {
					maxJump = Math.max(maxJump, (int) Math.round(Math.abs(centre - previousCentre)));
				}
				previousCentre = centre;
			}
		}
		m.minFret = minFret == Integer.MAX_VALUE ? 0 : minFret;
		m.maxFret = maxFret;
		m.maxJump = maxJump;
		// the busiest second: the most events whose onsets lie within one second
		int from = 0;
		int peak = 0;
		for (int i = 0; i < events.size(); i++) {
			while (events.get(i).onsetNanos() - events.get(from).onsetNanos() >= 1_000_000_000L) {
				from++;
			}
			peak = Math.max(peak, i - from + 1);
		}
		m.peakPerSecond = peak;
		m.durationSeconds = timeline.durationNanos() / 1e9;
		int previousTempo = -1;
		for (java.util.Iterator<TGMeasureHeader> headers = song.getMeasureHeaders(); headers.hasNext();) {
			TGMeasureHeader header = headers.next();
			if (header.isRepeatOpen() || header.getRepeatClose() > 0 || header.getRepeatAlternative() > 0) {
				m.repeats++;
			}
			int tempo = header.getTempo().getQuarterValue();
			if (previousTempo >= 0 && tempo != previousTempo) {
				m.tempoChanges++;
			}
			previousTempo = tempo;
		}
		return m;
	}

	/** One line of text, the same for the same metrics: the cache and the reports use it. */
	public String toTsv() {
		return String.format(Locale.ROOT, "%d\t%d\t%d\t%d\t%d\t%d\t%.0f\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%.1f\t%d", this.trackNumber, this.events, this.notes, this.chords,
				this.others, this.longestChordRun, this.peakPerSecond, this.namedChords, this.unnamedChords, this.maxChordSize, this.minFret, this.maxFret, this.maxJump,
				this.strings, this.tracks, this.repeats, this.durationSeconds, this.tempoChanges) + "\t" + (this.sequenceMonotonic ? 1 : 0);
	}

	public static TrackMetrics fromTsv(String line) {
		String[] f = line.split("\t");
		TrackMetrics m = new TrackMetrics();
		m.trackNumber = Integer.parseInt(f[0]);
		m.events = Integer.parseInt(f[1]);
		m.notes = Integer.parseInt(f[2]);
		m.chords = Integer.parseInt(f[3]);
		m.others = Integer.parseInt(f[4]);
		m.longestChordRun = Integer.parseInt(f[5]);
		m.peakPerSecond = Double.parseDouble(f[6]);
		m.namedChords = Integer.parseInt(f[7]);
		m.unnamedChords = Integer.parseInt(f[8]);
		m.maxChordSize = Integer.parseInt(f[9]);
		m.minFret = Integer.parseInt(f[10]);
		m.maxFret = Integer.parseInt(f[11]);
		m.maxJump = Integer.parseInt(f[12]);
		m.strings = Integer.parseInt(f[13]);
		m.tracks = Integer.parseInt(f[14]);
		m.repeats = Integer.parseInt(f[15]);
		m.durationSeconds = Double.parseDouble(f[16]);
		m.tempoChanges = Integer.parseInt(f[17]);
		m.sequenceMonotonic = f.length > 18 && f[18].equals("1");
		return m;
	}

	@Override
	public String toString() {
		return String.format(Locale.ROOT, "events %d (notes %d, chords %d: %d named, %d unnamed; longest run %d; largest %d notes), peak %.0f events/s, frets %d-%d (largest jump %d), "
				+ "%d strings, %d tracks, %d repeats, %d tempo changes, %.0f s", this.events, this.notes, this.chords, this.namedChords, this.unnamedChords,
				this.longestChordRun, this.maxChordSize, this.peakPerSecond, this.minFret, this.maxFret, this.maxJump, this.strings, this.tracks, this.repeats,
				this.tempoChanges, this.durationSeconds);
	}
}
