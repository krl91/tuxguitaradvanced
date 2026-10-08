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
		public final long nowNanos;
		public final ArcadeRun.View run;

		Reference(String name, SceneFixture fixture, long nowNanos, ArcadeRun.View run) {
			this.name = name;
			this.fixture = fixture;
			this.nowNanos = nowNanos;
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
		long now = fixture.timeline.nanosAtTick(fixture.timeline.targetTicks(3) + 144);
		return new Reference("six-strings", fixture, now, verdicts(8, ArcadeVerdict.ON_TIME, ArcadeVerdict.EARLY, ArcadeVerdict.INCOMPLETE, ArcadeVerdict.LATE));
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
		long now = fixture.timeline.nanosAtTick(fixture.timeline.targetTicks(3) + 144);
		return new Reference("four-strings", fixture, now, verdicts(8, ArcadeVerdict.ON_TIME, ArcadeVerdict.ON_TIME, ArcadeVerdict.NOT_JUDGED, ArcadeVerdict.MISSED));
	}
}
