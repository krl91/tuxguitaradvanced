package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.model.PracticeSequence;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.session.FollowSettings.Mode;
import app.tuxguitar.app.tools.custom.practice.session.TimingTolerance.Preset;
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
	public void theWaitModeIsTheDefaultAtTheTempoOfTheScoreWithTheNormalTolerance() {
		FollowSettings settings = new FollowSettings(new RecognitionSettingsStore(new Memory()));

		assertEquals(Mode.WAIT, settings.getMode());
		assertFalse(settings.isFollow());
		assertEquals(100, settings.getPercent());
		assertEquals(Preset.NORMAL, settings.getTolerance());
	}

	@Test
	public void changingOneValueNeverChangesAnother() {
		FollowSettings settings = new FollowSettings(new RecognitionSettingsStore(new Memory()));
		settings.setMode(Mode.FOLLOW);
		settings.setPercent(70);
		settings.setTolerance(Preset.STRICT);

		settings.setPercent(85);
		assertEquals(Mode.FOLLOW, settings.getMode());
		assertEquals(Preset.STRICT, settings.getTolerance());

		settings.setTolerance(Preset.LENIENT);
		assertEquals(85, settings.getPercent());
		assertEquals(Mode.FOLLOW, settings.getMode());

		settings.setMode(Mode.WAIT);
		assertEquals(85, settings.getPercent(), "going back to the wait mode keeps what was chosen for the next time");
		assertEquals(Preset.LENIENT, settings.getTolerance());
		settings.setMode(null);
		assertEquals(Mode.WAIT, settings.getMode());
		settings.setTolerance(null);
		assertEquals(Preset.NORMAL, settings.getTolerance());
	}

	@Test
	public void theToleranceStartsFromTheSavedConfigurationAndReturnsToItWhenItChanges() {
		Memory file = new Memory();
		RecognitionSettingsStore saved = new RecognitionSettingsStore(file);
		saved.update(saved.get().with(RecognitionParameters.TEMPO_TOLERANCE, RecognitionParameters.TOLERANCE_STRICT));

		FollowSettings settings = new FollowSettings(new RecognitionSettingsStore(file));
		assertEquals(Preset.STRICT, settings.getTolerance(), "the saved value");

		settings.setTolerance(Preset.LENIENT);
		assertEquals(Preset.LENIENT, settings.getTolerance(), "this run's choice");
		assertEquals(RecognitionParameters.TOLERANCE_STRICT, new RecognitionSettingsStore(file).get().get(RecognitionParameters.TEMPO_TOLERANCE), "not saved");

		RecognitionSettingsStore store = new RecognitionSettingsStore(new Memory());
		FollowSettings live = new FollowSettings(store);
		live.setTolerance(Preset.LENIENT);
		store.update(store.get().with(RecognitionParameters.TEMPO_TOLERANCE, RecognitionParameters.TOLERANCE_LENIENT));
		store.update(store.get().with(RecognitionParameters.TEMPO_TOLERANCE, RecognitionParameters.TOLERANCE_STRICT));
		assertEquals(Preset.STRICT, live.getTolerance(), "confirming the Configuration window resets the run choice");
	}

	@Test
	public void everySavedNameHasItsPresetAndAnythingElseIsNormal() {
		assertEquals(Preset.STRICT, FollowSettings.presetOf("strict"));
		assertEquals(Preset.NORMAL, FollowSettings.presetOf("normal"));
		assertEquals(Preset.LENIENT, FollowSettings.presetOf("lenient"));
		assertEquals(Preset.NORMAL, FollowSettings.presetOf("unknown"));
		assertEquals(Preset.NORMAL, FollowSettings.presetOf(null));
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
	public void arcadeIsAThirdModeThatSharesThePercentageAndTheToleranceWithFollowingAndChangesNothingOfThem() {
		FollowSettings settings = new FollowSettings(new RecognitionSettingsStore(new Memory()));
		settings.setPercent(80);
		settings.setTolerance(Preset.STRICT);

		settings.setMode(Mode.ARCADE);
		assertTrue(settings.isArcade());
		assertFalse(settings.isFollow(), "following and Arcade are two modes");
		assertTrue(settings.isTimed(), "both are played at the tempo of the score and judged on time");
		assertEquals(80, settings.getPercent());
		assertEquals(Preset.STRICT, settings.getTolerance());

		settings.setMode(Mode.FOLLOW);
		assertFalse(settings.isArcade());
		assertTrue(settings.isTimed());
		settings.setMode(Mode.WAIT);
		assertFalse(settings.isTimed());
		assertEquals(80, settings.getPercent(), "the choices stay for the next time, in memory only");
	}
}
