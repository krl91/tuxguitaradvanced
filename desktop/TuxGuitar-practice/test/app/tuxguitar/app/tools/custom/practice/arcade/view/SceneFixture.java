package app.tuxguitar.app.tools.custom.practice.arcade.view;

import java.util.ArrayList;
import java.util.List;

import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeVerdict;
import app.tuxguitar.app.tools.custom.practice.model.PracticeEvent;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.model.TrackTuning;
import app.tuxguitar.app.tools.custom.practice.session.FollowTimeline;

/** A song, its events as the view sees them, and the scene built on them, for the scene tests. */
final class SceneFixture {

	static final long MS = 1_000_000L;

	static final SceneLabels ENGLISH = new SceneLabels() {
		@Override
		public String verdict(ArcadeVerdict verdict) {
			switch (verdict) {
				case ON_TIME:
					return "On time";
				case EARLY:
					return "Early";
				case LATE:
					return "Late";
				case WRONG:
					return "Wrong";
				case INCOMPLETE:
					return "Incomplete";
				case MISSED:
					return "Missed";
				case NOT_JUDGED:
					return "Not judged";
				case AUTO_PASSED:
					return "Passed";
				default:
					return "Waiting";
			}
		}

		@Override
		public String chordFallback() {
			return "Chord";
		}

		@Override
		public String automatic() {
			return "Automatic";
		}

		@Override
		public String unsupported() {
			return "Not supported";
		}

		@Override
		public String missing(String names) {
			return "Missing: " + names;
		}

		@Override
		public String notCheckedSeparately() {
			return "Not checked separately";
		}

		@Override
		public String streak(int streak) {
			return "Streak " + streak;
		}

		@Override
		public String speed(int percent) {
			return percent + " %";
		}
	};

	final SongFixture song;
	final List<ArcadeRenderEvent> events;
	final NeckModel neck;
	final StringColours colours;
	final CameraTrack camera;

	SceneFixture(SongFixture song, TrackTuning tuning, int percent) {
		this.song = song;
		FollowTimeline timeline = FollowTimeline.build(song.song, song.sequence(), percent);
		List<PracticeEvent> practice = song.sequence().getEvents();
		this.events = new ArrayList<ArcadeRenderEvent>();
		for (int i = 0; i < practice.size(); i++) {
			this.events.add(ArcadeRenderEvent.of(i, practice.get(i), timeline));
		}
		this.neck = NeckModel.of(tuning, 24);
		this.colours = StringColours.forStrings(tuning.getStringCount());
		this.camera = CameraTrack.build(this.events, this.neck, CameraTrack.Settings.standard());
	}

	static TrackTuning sixStrings() {
		return new TrackTuning(new int[] { 64, 59, 55, 50, 45, 40 }, 0, null);
	}

	ArcadeScene scene(boolean monochrome) {
		return new ArcadeScene(this.events, this.neck, this.colours, this.camera, ENGLISH, monochrome);
	}

	long onset(int index) {
		return this.events.get(index).onsetNanos();
	}
}
