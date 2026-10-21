package app.tuxguitar.app.tools.custom.practice.session;

import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.LEAD;
import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.T0;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeLevel;
import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeLevelFilter;
import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeStarter;
import app.tuxguitar.app.tools.custom.practice.model.PracticeEvent;
import app.tuxguitar.app.tools.custom.practice.model.PracticeSequence;
import app.tuxguitar.app.tools.custom.practice.model.PracticeSequenceBuilder;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.model.TrackTuning;
import app.tuxguitar.app.tools.custom.practice.testsupport.FakeMidiPlayer;
import app.tuxguitar.app.tools.custom.practice.testsupport.FakeTempoClock;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.song.models.TGTrack;
import app.tuxguitar.util.TGContext;

/**
 * Spec 014 (T020, FR-008, FR-011, SC-004): a run prepared on the sequence a level kept contains only the kept sounds, at their written times (the times still
 * come from the song), and level 5 gives the run of before. What the Arcade window does with the level (the filter on the fresh-start path only, a restart and a
 * replay reusing the filtered sequence) is checked on its source by {@code ArcadeLevelWiringSourceTest}.
 */
public class ArcadeLevelRunTest {

	private static final class Environment implements FollowStarter.Environment {
		final AtomicReference<FollowTimeline> timeline = new AtomicReference<FollowTimeline>();
		final FakeTempoClock clock = new FakeTempoClock(ticks -> {
			long whole = (long) Math.floor(ticks);
			double part = ticks - whole;
			FollowTimeline built = Environment.this.timeline.get();
			return built.nanosAtTick(whole) * (1.0 - part) + built.nanosAtTick(whole + 1) * part;
		}, FollowStarter.FIRST_TICK, 80_000L);
		final FakeMidiPlayer player = new FakeMidiPlayer(this.clock).withInitialState(100, false, true).countDownNanos(LEAD);
		final RouteCalibrations calibrations = new RouteCalibrations();

		Environment() {
			this.clock.setNow(T0);
		}

		public FollowPlayer player() {
			return this.player;
		}

		public long wallNanosOf(AnalysisFrame frame) {
			return T0 + frame.getTimestampNanos();
		}

		public boolean captureReady() {
			return true;
		}

		public double lagSpreadMillis() {
			return 5.0;
		}

		public AudioRoute route() {
			return new AudioRoute("input", "output", 48000, "1ch 16bit");
		}

		public RouteCalibrations calibrations() {
			return this.calibrations;
		}

		public InputAssessment assessment() {
			return new InputAssessment(InputAssessment.Status.SUITABLE, InputAssessment.Reason.NONE);
		}
	}


	private static SongFixture eightNotes() {
		SongFixture fixture = new SongFixture(2);
		for (int i = 0; i < 8; i++) {
			fixture.quarter(i / 4, i % 4, 1, i);
		}
		return fixture;
	}

	private static FollowStarter.Plan plan(SongFixture fixture, PracticeSequence sequence) {
		TGTrack track = fixture.song.getTrack(0);
		Environment environment = new Environment();
		FollowSettings settings = new FollowSettings();
		settings.setMode(FollowSettings.Mode.ARCADE);
		FollowStarter.Plan plan = ArcadeStarter.prepareFrom(new TGContext(), fixture.song, track, sequence, settings, PracticeParameters.of(RecognitionSettings.defaults()), null, null,
				environment, s -> { }, Runnable::run, r -> { }, null, null);
		environment.timeline.set(plan.timeline());
		return plan;
	}

	private static PracticeSequence whole(SongFixture fixture) {
		TGTrack track = fixture.song.getTrack(0);
		return PracticeSequenceBuilder.build(fixture.song, track, TrackTuning.ofAnyStrings(track), null, 0L);
	}

	@Test
	public void aRunAtLevelThreeContainsOnlyTheKeptSoundsAtTheirWrittenTimes() {
		SongFixture fixture = eightNotes();
		PracticeSequence full = whole(fixture);
		FollowStarter.Plan everything = plan(fixture, full);
		ArcadeLevelFilter.Result result = ArcadeLevelFilter.apply(full, ArcadeLevel.of(3));
		FollowStarter.Plan levelled = plan(fixture, result.kept());
		assertEquals(4, levelled.sequence().getEvents().size(), "50 % of 8 notes");
		List<Integer> original = new ArrayList<Integer>();
		for (PracticeEvent kept : levelled.sequence().getEvents()) {
			original.add(full.getEvents().indexOf(kept));
		}
		assertEquals(List.of(0, 2, 4, 6), original, "the first sound and every other one after it");
		for (int i = 0; i < original.size(); i++) {
			assertEquals(everything.timeline().targetTicks(original.get(i)), levelled.timeline().targetTicks(i), "the sound " + original.get(i) + " keeps its written time");
		}
	}

	@Test
	public void levelFiveGivesTheRunOfBeforeThisFeature() {
		SongFixture fixture = eightNotes();
		PracticeSequence full = whole(fixture);
		FollowStarter.Plan before = plan(fixture, full);
		PracticeSequence kept = ArcadeLevelFilter.apply(full, ArcadeLevel.of(5)).kept();
		assertSame(full, kept, "nothing is removed: the very same sequence");
		FollowStarter.Plan after = plan(fixture, kept);
		assertEquals(before.sequence().getEvents(), after.sequence().getEvents());
		for (int i = 0; i < 8; i++) {
			assertEquals(before.timeline().targetTicks(i), after.timeline().targetTicks(i));
		}
	}

	@Test
	public void aReplayOfASectionFromTheFilteredWholeKeepsTheLevelAndDoesNotFilterAgain() {
		SongFixture fixture = eightNotes();
		PracticeSequence whole = ArcadeLevelFilter.apply(whole(fixture), ArcadeLevel.of(3)).kept();
		assertEquals(4, whole.getEvents().size());
		// the replay starts from the measure chosen, on the already filtered whole (what the result window gives back)
		PracticeSequence fromSecondMeasure = whole.startingAt(2);
		assertEquals(2, fromSecondMeasure.getEvents().size(), "the kept sounds of the second measure, no further reduction");
		PracticeSequence reduced = ArcadeLevelFilter.apply(fromSecondMeasure, ArcadeLevel.of(3)).kept();
		assertEquals(false, reduced.getEvents().size() == fromSecondMeasure.getEvents().size(), "filtering a second time would reduce it again: this is what the window must not do");
	}
}
