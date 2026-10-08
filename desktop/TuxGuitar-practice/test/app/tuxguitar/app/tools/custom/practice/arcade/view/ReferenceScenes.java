package app.tuxguitar.app.tools.custom.practice.arcade.view;

import java.util.ArrayList;
import java.util.List;

import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeRun;
import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeVerdict;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.song.models.TGChord;
import app.tuxguitar.song.models.TGStroke;

/**
 * The fixed scenes that stand for the approved look (spec 003 T043, T044): a six-string guitar passage and a four-string bass passage, each at a fixed
 * instant with a few verdicts shown. The structure of these scenes is compared to the reference files, and the owner reviews their images against the
 * approved concept images (composition, not pixels) before the files become the approved reference.
 */
public final class ReferenceScenes {

	public static final Viewport SCREEN = new Viewport(1280, 720);
	public static final long MS = SceneFixture.MS;

	public static final class Reference {
		public final String name;
		public final SceneFixture fixture;
		/** The instant, as a tick counted from the first played measure: a whole tick, so that the clock of a real player maps back to exactly this scene. */
		public final long tick;
		public final long nowNanos;
		public final ArcadeRun.View run;

		Reference(String name, SceneFixture fixture, long tick, ArcadeRun.View run) {
			this.name = name;
			this.fixture = fixture;
			this.tick = tick;
			this.nowNanos = fixture.timeline.nanosAtTick(tick);
			this.run = run;
		}

		public Scene scene(boolean monochrome, QualityTier tier) {
			return this.fixture.scene(monochrome).build(this.nowNanos, SCREEN, this.run, 100, tier);
		}
	}

	private ReferenceScenes() {
	}

	private static ArcadeRun.View verdicts(int count, ArcadeVerdict... first) {
		List<ArcadeRun.EventState> states = new ArrayList<ArcadeRun.EventState>();
		for (int i = 0; i < count; i++) {
			states.add(new ArcadeRun.EventState(i, i < first.length ? first[i] : ArcadeVerdict.WAITING, 0L, 0, 0, java.util.Collections.emptyList(),
					i == 2 && first.length > 2 && first[2] == ArcadeVerdict.INCOMPLETE ? new int[] { 52 } : new int[0]));
		}
		return new ArcadeRun.View(states, 0, 12, 12, 0, first.length);
	}

	/** A guitar: single notes on several strings, an A5 chord named and down-stroked, an open chord, a fast approach of notes. */
	public static Reference sixStrings() {
		SongFixture song = new SongFixture(2);
		song.quarter(0, 0, 6, 3);
		song.quarter(0, 1, 4, 5);
		song.chord(0, 2, new int[] { 6, 5, 4 }, new int[] { 5, 7, 7 });
		song.quarter(0, 3, 2, 8);
		song.chord(1, 0, new int[] { 6, 5, 4, 3, 2, 1 }, new int[] { 0, 2, 2, 1, 0, 0 });
		song.quarter(1, 1, 1, 12);
		song.quarter(1, 2, 3, 7);
		song.quarter(1, 3, 5, 10);
		TGChord named = song.factory().newChord(6);
		named.setName("A5");
		song.track().getMeasure(0).getBeat(2).setChord(named);
		song.track().getMeasure(0).getBeat(2).getStroke().setDirection(TGStroke.STROKE_DOWN);
		SceneFixture fixture = new SceneFixture(song, SceneFixture.sixStrings(), 100);
		long tick = fixture.timeline.targetTicks(3) + 144;
		return new Reference("six-strings", fixture, tick, verdicts(8, ArcadeVerdict.ON_TIME, ArcadeVerdict.EARLY, ArcadeVerdict.INCOMPLETE, ArcadeVerdict.LATE));
	}

	/** A bass: a line on the four strings, open strings, an octave pair written as a chord, a note high on the neck. */
	public static Reference fourStrings() {
		SongFixture song = new SongFixture(2);
		song.quarter(0, 0, 4, 0);
		song.quarter(0, 1, 3, 2);
		song.chord(0, 2, new int[] { 4, 2 }, new int[] { 3, 5 });
		song.quarter(0, 3, 1, 12);
		song.quarter(1, 0, 2, 0);
		song.quarter(1, 1, 4, 5);
		song.quarter(1, 2, 3, 7);
		song.quarter(1, 3, 1, 3);
		SceneFixture fixture = new SceneFixture(song, TrackTuningFactory.any(43, 38, 33, 28), 100);
		long tick = fixture.timeline.targetTicks(3) + 144;
		return new Reference("four-strings", fixture, tick, verdicts(8, ArcadeVerdict.ON_TIME, ArcadeVerdict.ON_TIME, ArcadeVerdict.NOT_JUDGED, ArcadeVerdict.MISSED));
	}

	/** A named chord (A5, down-stroked) coming in with a note and another chord behind it: the header of the diagram, the fret label, the ghost on the neck. */
	public static Reference namedChord() {
		SongFixture song = new SongFixture(3);
		song.chord(1, 0, new int[] { 6, 5, 4 }, new int[] { 5, 7, 7 });
		song.quarter(1, 1, 3, 5);
		song.chord(1, 3, new int[] { 6, 5, 4, 3, 2, 1 }, new int[] { 3, 5, 5, 4, 3, 3 });
		song.quarter(2, 1, 2, 8);
		TGChord named = song.factory().newChord(6);
		named.setName("A5");
		song.track().getMeasure(1).getBeat(0).setChord(named);
		song.track().getMeasure(1).getBeat(0).getStroke().setDirection(TGStroke.STROKE_DOWN);
		TGChord other = song.factory().newChord(6);
		other.setName("Gmaj7");
		song.track().getMeasure(1).getBeat(2).setChord(other);
		SceneFixture fixture = new SceneFixture(song, SceneFixture.sixStrings(), 100);
		long tick = fixture.timeline.targetTicks(0) - 1700;
		return new Reference("chord-named", fixture, tick, null);
	}

	/** An unnamed chord: the localised generic word, nothing derived from the notes. */
	public static Reference unnamedChord() {
		SongFixture song = new SongFixture(3);
		song.chord(1, 0, new int[] { 5, 4, 3, 2 }, new int[] { 3, 2, 0, 1 });
		song.quarter(1, 2, 3, 5);
		song.quarter(2, 0, 2, 7);
		SceneFixture fixture = new SceneFixture(song, SceneFixture.sixStrings(), 100);
		long tick = fixture.timeline.targetTicks(0) - 1700;
		return new Reference("chord-unnamed", fixture, tick, null);
	}

	/** A single note with a chord behind it: the note has its fret number and no chord header at all. */
	public static Reference singleNote() {
		SongFixture song = new SongFixture(3);
		song.quarter(1, 0, 3, 5);
		song.quarter(1, 2, 2, 7);
		song.chord(2, 0, new int[] { 6, 5, 4 }, new int[] { 5, 7, 7 });
		TGChord named = song.factory().newChord(6);
		named.setName("A5");
		song.track().getMeasure(2).getBeat(0).setChord(named);
		SceneFixture fixture = new SceneFixture(song, SceneFixture.sixStrings(), 100);
		long tick = fixture.timeline.targetTicks(0) - 1150;
		return new Reference("note-single", fixture, tick, null);
	}

	/**
	 * A realistic fast passage, not the artificial stress of the benchmark: a pentatonic run at five notes a second with a chord on every fifth beat, the
	 * first notes just played and judged. Fifteen events are inside the look-ahead; four at most are detailed, the others are previews.
	 */
	public static Reference denseRealistic() {
		SongFixture song = new SongFixture(6);
		song.tempoChange(0, 300);
		int[][] run = { { 3, 5 }, { 3, 8 }, { 2, 5 }, { 2, 7 }, { 2, 8 }, { 1, 5 }, { 1, 8 }, { 1, 5 }, { 2, 8 }, { 2, 7 }, { 3, 8 }, { 3, 5 }, { 4, 7 }, { 4, 5 }, { 3, 7 },
				{ 3, 5 }, { 2, 8 }, { 2, 5 }, { 1, 5 }, { 1, 8 }, { 1, 5 }, { 2, 8 }, { 2, 5 }, { 3, 7 } };
		for (int i = 0; i < run.length; i++) {
			if (i % 5 == 4) {
				song.chord(i / 4, i % 4, new int[] { 5, 4, 3 }, new int[] { 0, 2, 2 });
			} else {
				song.quarter(i / 4, i % 4, run[i][0], run[i][1]);
			}
		}
		TGChord am = song.factory().newChord(6);
		am.setName("Am");
		song.track().getMeasure(1).getBeat(0).setChord(am);
		SceneFixture fixture = new SceneFixture(song, SceneFixture.sixStrings(), 100);
		long tick = fixture.timeline.targetTicks(5) + 300;
		ArcadeVerdict[] judged = { ArcadeVerdict.ON_TIME, ArcadeVerdict.ON_TIME, ArcadeVerdict.EARLY, ArcadeVerdict.ON_TIME, ArcadeVerdict.LATE };
		return new Reference("dense-passage", fixture, tick, verdicts(fixture.events.size(), judged));
	}

	/** Every reference scene, in the order of the review. */
	public static Reference[] all() {
		return new Reference[] { sixStrings(), fourStrings(), namedChord(), unnamedChord(), singleNote(), denseRealistic() };
	}
}
