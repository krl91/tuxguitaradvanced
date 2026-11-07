package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeLeniency;
import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeLeniencyPosition;
import app.tuxguitar.audio.engine.settings.RecognitionParameter;
import app.tuxguitar.audio.engine.settings.RecognitionParameter.Section;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;

/**
 * The leniency families (spec 026, T025, inventory.md "LENIENCY_1" to "LENIENCY_4"): one value per relaxed row and position, their defaults the former
 * values of Follow's and Arcade's rows and of Arcade's window factors, so the relaxed settings are the same as before.
 */
public class LeniencyParametersTest {

	@Test
	public void theDefaultsGiveTheFormerRelaxedSettings() {
		RecognitionSettings d = RecognitionSettings.defaults();
		assertEquals(Arrays.asList(35.0, 0.60, 55.0, 0.22, 1.0, 24.0), values(FollowLeniency.apply(FollowLeniencyPosition.PERMISSIVE, d), FOLLOW_ROWS));
		assertEquals(Arrays.asList(35.0, 0.50, 50.0, 0.20, 1.0, 20.0), values(FollowLeniency.apply(FollowLeniencyPosition.VERY_PERMISSIVE, d), FOLLOW_ROWS));
		assertEquals(Arrays.asList(0.70, 75.0, 0.28, 2.0, 32.0), values(ArcadeLeniency.apply(ArcadeLeniencyPosition.FUN, d), ARCADE_ROWS));
		assertEquals(Arrays.asList(0.60, 55.0, 0.22, 1.0, 24.0), values(ArcadeLeniency.apply(ArcadeLeniencyPosition.VERY_PERMISSIVE, d), ARCADE_ROWS));
		assertEquals(94, ArcadeLeniency.windowMillis(ArcadeLeniencyPosition.FUN, 75, d));
		assertEquals(113, ArcadeLeniency.windowMillis(ArcadeLeniencyPosition.VERY_PERMISSIVE, 75, d));
		assertEquals(ArcadeLeniency.windowMillis(ArcadeLeniencyPosition.FUN, 75), ArcadeLeniency.windowMillis(ArcadeLeniencyPosition.FUN, 75, d));
	}

	@Test
	public void theRelaxedSettingsCarryTheValues() {
		RecognitionSettings s = RecognitionSettings.defaults().with(RecognitionParameters.FOLLOW_LENIENCY_PERMISSIVE_ACCEPT, 40)
				.with(RecognitionParameters.FOLLOW_LENIENCY_VERY_PERMISSIVE_STABILITY, 70)
				.with(RecognitionParameters.ARCADE_LENIENCY_FUN_CONFIDENCE, 0.65).with(RecognitionParameters.ARCADE_LENIENCY_VERY_PERMISSIVE_TEMPO_WINDOW, 2.0);
		assertEquals(40.0, FollowLeniency.apply(FollowLeniencyPosition.PERMISSIVE, s).get(RecognitionParameters.NOTE_ACCEPT));
		assertEquals(70.0, FollowLeniency.apply(FollowLeniencyPosition.VERY_PERMISSIVE, s).get(RecognitionParameters.NOTE_STABILITY));
		assertEquals(0.65, ArcadeLeniency.apply(ArcadeLeniencyPosition.FUN, s).get(RecognitionParameters.NOTE_CONFIDENCE));
		assertEquals(150, ArcadeLeniency.windowMillis(ArcadeLeniencyPosition.VERY_PERMISSIVE, 75, s));
	}

	@Test
	public void theSectionsHoldOneValuePerRowAndPosition() {
		assertEquals(6, RecognitionParameters.of(Section.LENIENCY_1).size());
		assertEquals(6, RecognitionParameters.of(Section.LENIENCY_2).size());
		assertEquals(6, RecognitionParameters.of(Section.LENIENCY_3).size());
		assertEquals(6, RecognitionParameters.of(Section.LENIENCY_4).size());
		// each value has the bounds of the setting it relaxes
		List<RecognitionParameter> follow = new ArrayList<RecognitionParameter>(RecognitionParameters.of(Section.LENIENCY_1));
		for( int i = 0 ; i < FOLLOW_ROWS.size() ; i ++ ) {
			RecognitionParameter.Number value = (RecognitionParameter.Number) follow.get(i);
			assertEquals(FOLLOW_ROWS.get(i).getMinimum(), value.getMinimum(), value.getKey());
			assertEquals(FOLLOW_ROWS.get(i).getMaximum(), value.getMaximum(), value.getKey());
		}
	}

	private static final List<RecognitionParameter.Number> FOLLOW_ROWS = Arrays.asList(RecognitionParameters.NOTE_ACCEPT, RecognitionParameters.NOTE_CONFIDENCE,
			RecognitionParameters.NOTE_STABILITY, RecognitionParameters.CHORD_EVIDENCE, RecognitionParameters.CHORD_MIN_PARTIALS, RecognitionParameters.CHORD_CONFIRM);
	private static final List<RecognitionParameter.Number> ARCADE_ROWS = Arrays.asList(RecognitionParameters.NOTE_CONFIDENCE, RecognitionParameters.NOTE_STABILITY,
			RecognitionParameters.CHORD_EVIDENCE, RecognitionParameters.CHORD_MIN_PARTIALS, RecognitionParameters.CHORD_CONFIRM);

	private static List<Double> values(RecognitionSettings s, List<RecognitionParameter.Number> rows) {
		List<Double> values = new ArrayList<Double>();
		for( RecognitionParameter.Number row : rows ) {
			values.add(s.get(row));
		}
		return values;
	}
}
