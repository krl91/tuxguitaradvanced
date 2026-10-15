package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.model.PracticeSequence;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.session.FollowSettings.Mode;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettingsStore;

/** The choices of the preparation window for the tempo-following mode (spec 002 T098): the wait mode by default, each value its own, the tolerance from the Configuration. */
public class FollowSettingsTest {

	private static final class Memory implements RecognitionSettingsStore.Persistence {
		java.util.Map<String, String> values = new java.util.HashMap<String, String>();

		public java.util.Map<String, String> read() {
			return new java.util.HashMap<String, String>(this.values);
		}

		public void write(java.util.Map<String, String> values) {
			this.values = new java.util.HashMap<String, String>(values);
		}
	}

	@Test
	public void theWaitModeIsTheDefaultAtTheTempoOfTheScoreWithTheDefaultWindowOfOneHundredMilliseconds() {
		FollowSettings settings = new FollowSettings(new RecognitionSettingsStore(new Memory()));

		assertEquals(Mode.WAIT, settings.getMode());
		assertFalse(settings.isFollow());
		assertEquals(100, settings.getPercent());
		assertEquals(100, settings.getWindowMillis(), "a third more lenient than the old Normal (75 ms)");
	}

	@Test
	public void changingOneValueNeverChangesAnother() {
		FollowSettings settings = new FollowSettings(new RecognitionSettingsStore(new Memory()));
		settings.setMode(Mode.FOLLOW);
		settings.setPercent(70);
		settings.setWindowMillis(60);

		settings.setPercent(85);
		assertEquals(Mode.FOLLOW, settings.getMode());
		assertEquals(60, settings.getWindowMillis());

		settings.setWindowMillis(180);
		assertEquals(85, settings.getPercent());
		assertEquals(Mode.FOLLOW, settings.getMode());

		settings.setMode(Mode.WAIT);
		assertEquals(85, settings.getPercent(), "going back to the wait mode keeps what was chosen for the next time");
		assertEquals(180, settings.getWindowMillis());
		settings.setMode(null);
		assertEquals(Mode.WAIT, settings.getMode());
	}

	@Test
	public void theWindowIsOneSettingSharedWithTheStoreSavedAtOnceAndFollowedWhenTheConfigurationChangesIt() {
		Memory file = new Memory();
		RecognitionSettingsStore saved = new RecognitionSettingsStore(file);
		saved.update(saved.get().with(RecognitionParameters.TEMPO_WINDOW, 150.0));

		FollowSettings settings = new FollowSettings(new RecognitionSettingsStore(file));
		assertEquals(150, settings.getWindowMillis(), "the saved value");

		settings.setWindowMillis(70);
		assertEquals(70, settings.getWindowMillis());
		assertEquals(70.0, new RecognitionSettingsStore(file).get().get(RecognitionParameters.TEMPO_WINDOW), "saved at once: one setting, not a choice of this run only");

		RecognitionSettingsStore store = new RecognitionSettingsStore(new Memory());
		FollowSettings live = new FollowSettings(store);
		java.util.concurrent.atomic.AtomicInteger changes = new java.util.concurrent.atomic.AtomicInteger();
		store.addListener((previous, current) -> changes.incrementAndGet());
		live.setWindowMillis(130);
		assertEquals(1, changes.get(), "the Configuration window is told once");
		store.update(store.get().with(RecognitionParameters.TEMPO_WINDOW, 45.0));
		assertEquals(45, live.getWindowMillis(), "a change made in the Configuration window is the same setting");
	}

	@Test
	public void theWindowIsBroughtIntoItsRangeAndAnEmptyFollowSettingsHasTheDefault() {
		FollowSettings settings = new FollowSettings(new RecognitionSettingsStore(new Memory()));
		settings.setWindowMillis(10);
		assertEquals(TimingTolerance.MIN_WIDTH_MILLIS, settings.getWindowMillis());
		settings.setWindowMillis(900);
		assertEquals(TimingTolerance.MAX_WIDTH_MILLIS, settings.getWindowMillis());
		assertEquals(100, new FollowSettings().getWindowMillis(), "a window that has no store");
		assertTrue(FollowSettings.MIN_PERCENT < FollowSettings.DEFAULT_PERCENT && FollowSettings.DEFAULT_PERCENT < FollowSettings.MAX_PERCENT);
	}

	@Test
	public void aSequenceStartingAtAnEventKeepsTheTimesOfTheSongAndStartsAtItsFirstEvent() {
		SongFixture fixture = new SongFixture(2);
		for (int beat = 0; beat < 4; beat++) {
			fixture.quarter(0, beat, 1, beat);
		}
		fixture.quarter(1, 0, 1, 4);
		PracticeSequence full = fixture.sequence();

		PracticeSequence from = full.startingAt(4);
		assertEquals(1, from.getEvents().size());
		assertEquals(0, from.getStartIndex());
		assertEquals(full.getEvents().get(4), from.getEvents().get(0));
		assertEquals(FollowTimeline.build(fixture.song, full, 100).targetNanos(4), FollowTimeline.build(fixture.song, from, 100).targetNanos(0),
				"the time of the event is the same, from the start of the song");
		assertEquals(5, full.startingAt(-3).getEvents().size());
		assertEquals(0, full.startingAt(99).getEvents().size());
		assertEquals(5, full.getEvents().size(), "the sequence it comes from is not changed");
	}

	@Test
	public void arcadeIsAThirdModeThatSharesThePercentageAndTheWindowWithFollowingAndChangesNothingOfThem() {
		FollowSettings settings = new FollowSettings(new RecognitionSettingsStore(new Memory()));
		settings.setPercent(80);
		settings.setWindowMillis(60);

		settings.setMode(Mode.ARCADE);
		assertTrue(settings.isArcade());
		assertFalse(settings.isFollow(), "following and Arcade are two modes");
		assertTrue(settings.isTimed(), "both are played at the tempo of the score and judged on time");
		assertEquals(80, settings.getPercent());
		assertEquals(60, settings.getWindowMillis());

		settings.setMode(Mode.FOLLOW);
		assertFalse(settings.isArcade());
		assertTrue(settings.isTimed());
		settings.setMode(Mode.WAIT);
		assertFalse(settings.isTimed());
		assertEquals(80, settings.getPercent(), "the choices stay for the next time, in memory only");
	}

	/** The tilt of the Arcade plane (owner, 2026-10-11): the scale at the horizon and the height of the horizon, in percent, kept within bounds. */
	@Test
	public void theTiltOfTheArcadePlaneHasTheOwnersDefaultsAndStaysWithinItsBounds() {
		FollowSettings settings = new FollowSettings();
		assertEquals(35, settings.getFarScalePercent());
		assertEquals(18, settings.getHorizonPercent());
		assertEquals(FollowSettings.MAX_FAR_SCALE_PERCENT, settings.setFarScalePercent(500));
		assertEquals(FollowSettings.MIN_FAR_SCALE_PERCENT, settings.setFarScalePercent(-3));
		assertEquals(FollowSettings.MAX_HORIZON_PERCENT, settings.setHorizonPercent(90));
		assertEquals(FollowSettings.MIN_HORIZON_PERCENT, settings.setHorizonPercent(-1));
		assertEquals(40, settings.setHorizonPercent(40));
	}
}
