package app.tuxguitar.app.tools.custom.practice.arcade.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeRun;
import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeScoring;
import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeVerdict;
import app.tuxguitar.app.tools.custom.practice.arcade.view.ArcadeRenderEvent;
import app.tuxguitar.app.tools.custom.practice.arcade.view.ArcadeScene;
import app.tuxguitar.app.tools.custom.practice.arcade.view.CameraTrack;
import app.tuxguitar.app.tools.custom.practice.arcade.view.FlameState;
import app.tuxguitar.app.tools.custom.practice.arcade.view.NeckModel;
import app.tuxguitar.app.tools.custom.practice.arcade.view.QualityController;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneFixture;
import app.tuxguitar.app.tools.custom.practice.arcade.view.StringColours;
import app.tuxguitar.app.tools.custom.practice.arcade.view.Viewport;
import app.tuxguitar.app.tools.custom.practice.model.PracticeEvent;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.model.TrackTuning;
import app.tuxguitar.app.tools.custom.practice.session.FlameSetting;
import app.tuxguitar.app.tools.custom.practice.session.FollowTimeline;
import app.tuxguitar.app.tools.custom.practice.testsupport.FakeTempoClock;

/**
 * Spec 018 (T015; FR-005, FR-009): each event turned right in the run adds one burst of sparks to the flames, with the flame clock and the streak of that
 * moment; a wrong or missed note adds none; a pause holds the bursts as it holds the flame clock; a burst is dropped after one second. Driven as
 * {@code ArcadeFrameSourceTest} drives the source.
 */
public class ArcadeFrameSourceBurstsTest {

	private static final long FIRST_TICK = 960L;
	private static final long MS = 1_000_000L;
	private static final long STEP = 50 * MS;
	private static final Viewport SCREEN = new Viewport(1280, 720);

	private final FakeTempoClock clock;
	private final ArcadeScene scene;
	private final FollowTimeline timeline;

	/** The run as the picture sees it: the states of the events, turned one by one by the test. */
	private static final class Play implements java.util.function.Supplier<ArcadeRun.View> {
		final List<ArcadeRun.EventState> states = new ArrayList<ArcadeRun.EventState>();
		int streak;

		void judge(ArcadeVerdict verdict) {
			boolean right = verdict == ArcadeVerdict.ON_TIME || verdict == ArcadeVerdict.EARLY || verdict == ArcadeVerdict.LATE;
			this.streak = right ? this.streak + 1 : 0;
			this.states.add(new ArcadeRun.EventState(this.states.size(), verdict, 0L, right ? 100 : 0, this.streak, Collections.emptyList(), new int[0]));
		}

		@Override
		public ArcadeRun.View get() {
			int judged = this.states.size();
			return new ArcadeRun.View(new ArrayList<ArcadeRun.EventState>(this.states), 0, this.streak, this.streak, 0, judged);
		}
	}

	public ArcadeFrameSourceBurstsTest() {
		SongFixture song = new SongFixture(2);
		for (int i = 0; i < 8; i++) {
			song.quarter(i / 4, i % 4, 1 + i % 6, i * 2 + 1);
		}
		this.timeline = FollowTimeline.build(song.song, song.sequence(), 100);
		List<ArcadeRenderEvent> events = new ArrayList<ArcadeRenderEvent>();
		List<PracticeEvent> practice = song.sequence().getEvents();
		for (int i = 0; i < practice.size(); i++) {
			events.add(ArcadeRenderEvent.of(i, practice.get(i), timeline));
		}
		NeckModel neck = NeckModel.of(new TrackTuning(new int[] { 64, 59, 55, 50, 45, 40 }, 0, null), 24);
		this.scene = new ArcadeScene(events, neck, StringColours.forStrings(6), CameraTrack.build(events, neck, CameraTrack.Settings.standard()), SceneFixture.ENGLISH, false);
		this.clock = new FakeTempoClock(ticks -> timeline.nanosAtTick((long) ticks), FIRST_TICK, 100_000L);
	}

	private ArcadeFrameSource source(Play play) {
		return new ArcadeFrameSource(scene, timeline, FIRST_TICK, clock, play, () -> 100, new QualityController(ArcadeCanvas.FRAME_NANOS), 0L, 0L, FlameSetting.FULL, true,
				ArcadeScoring.standard());
	}

	private void picture(ArcadeFrameSource source) {
		source.frame(clock.nowNanos(), SCREEN);
		clock.advance(STEP);
	}

	@Test
	public void eachRightNoteAddsOneBurstWithTheFlameClockAndTheStreak() {
		clock.start(FIRST_TICK);
		Play play = new Play();
		ArcadeFrameSource source = source(play);
		picture(source);
		picture(source);
		assertTrue(source.bursts().isEmpty(), "nothing judged yet");
		play.judge(ArcadeVerdict.ON_TIME);
		picture(source);
		assertEquals(1, source.bursts().size());
		FlameState.Burst first = source.bursts().get(0);
		assertEquals(1, first.streak());
		assertTrue(first.clockNanos() > 0L && first.clockNanos() <= source.flameClockNanos(), "with the flame clock");
		play.judge(ArcadeVerdict.EARLY);
		play.judge(ArcadeVerdict.LATE);
		picture(source);
		assertEquals(3, source.bursts().size(), "two new right notes, two bursts");
		assertEquals(3, source.bursts().get(2).streak(), "with the streak of that moment");
		picture(source);
		assertEquals(3, source.bursts().size(), "a picture without a new right note adds none");
	}

	@Test
	public void aWrongOrMissedNoteAddsNone() {
		clock.start(FIRST_TICK);
		Play play = new Play();
		ArcadeFrameSource source = source(play);
		picture(source);
		play.judge(ArcadeVerdict.WRONG);
		play.judge(ArcadeVerdict.MISSED);
		play.judge(ArcadeVerdict.INCOMPLETE);
		play.judge(ArcadeVerdict.NOT_JUDGED);
		picture(source);
		assertTrue(source.bursts().isEmpty());
	}

	@Test
	public void aPauseHoldsTheBurstsAndABurstIsDroppedAfterOneSecond() {
		clock.start(FIRST_TICK);
		Play play = new Play();
		ArcadeFrameSource source = source(play);
		picture(source);
		play.judge(ArcadeVerdict.ON_TIME);
		picture(source);
		assertEquals(1, source.bursts().size());
		source.setPaused(true, clock.nowNanos());
		for (int i = 0; i < 40; i++) {
			picture(source);
		}
		assertEquals(1, source.bursts().size(), "two seconds in a pause: the burst has not aged");
		source.setPaused(false, clock.nowNanos());
		for (int i = 0; i < 24; i++) {
			picture(source);
		}
		assertTrue(source.bursts().isEmpty(), "dropped after one second of flame clock");
	}
}
