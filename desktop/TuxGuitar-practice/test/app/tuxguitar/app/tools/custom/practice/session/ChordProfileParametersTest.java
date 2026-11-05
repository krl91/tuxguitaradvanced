package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeLeniency;
import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeLeniencyPosition;
import app.tuxguitar.audio.engine.settings.RecognitionParameter;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;

/**
 * The profile values reach the chord matcher (spec 024, task T015; FR-009, FR-013): {@link PracticeParameters#chordParameters()} passes the rule
 * and the profile values; Follow and Arcade keep them (T007: no relaxation needed); a value changed in the settings reaches the next run.
 */
public class ChordProfileParametersTest {

	private static final RecognitionParameter[] PROFILE = { RecognitionParameters.CHORD_COMPLETENESS, RecognitionParameters.CHORD_PROFILE_MATCH,
			RecognitionParameters.CHORD_PROFILE_NAMES, RecognitionParameters.CHORD_ATTACK, RecognitionParameters.CHORD_PROFILE_TOP,
			RecognitionParameters.CHORD_PROFILE_SETTLE };

	@Test
	public void theChordParametersCarryTheRuleAndTheProfileValues() {
		ChordMatcher.Parameters d = PracticeParameters.of(RecognitionSettings.defaults()).chordParameters();
		assertEquals(RecognitionParameters.COMPLETENESS_PROFILE, d.rule);
		assertTrue(d.usesProfile());
		assertEquals(0.79, d.profileMatch, 1e-12);
		assertEquals(0.40, d.profileNames, 1e-12);
		assertEquals(84, d.profileTop);
		assertEquals(200_000_000L, d.profileSettleNanos);
		assertTrue(d.attack);
	}

	@Test
	public void aValueChangedInTheSettingsReachesTheNextRun() {
		RecognitionSettings changed = RecognitionSettings.defaults()
				.with(RecognitionParameters.CHORD_PROFILE_MATCH, 0.70)
				.with(RecognitionParameters.CHORD_PROFILE_NAMES, 0.25)
				.with(RecognitionParameters.CHORD_ATTACK, false)
				.with(RecognitionParameters.CHORD_PROFILE_TOP, 96.0)
				.with(RecognitionParameters.CHORD_PROFILE_SETTLE, 100.0);
		ChordMatcher.Parameters p = PracticeParameters.of(changed).chordParameters();
		assertEquals(0.70, p.profileMatch, 1e-12);
		assertEquals(0.25, p.profileNames, 1e-12);
		assertFalse(p.attack);
		assertEquals(96, p.profileTop);
		assertEquals(100_000_000L, p.profileSettleNanos);
		ChordMatcher.Parameters perString = PracticeParameters.of(changed.with(RecognitionParameters.CHORD_COMPLETENESS, RecognitionParameters.COMPLETENESS_ALL))
				.chordParameters();
		assertEquals(RecognitionParameters.COMPLETENESS_ALL, perString.rule);
		assertFalse(perString.usesProfile());
	}

	@Test
	public void followAndArcadeKeepTheProfileValues() {
		RecognitionSettings settings = RecognitionSettings.defaults().with(RecognitionParameters.CHORD_PROFILE_NAMES, 0.30);
		for( FollowLeniencyPosition position : FollowLeniencyPosition.values() ) {
			RecognitionSettings relaxed = FollowLeniency.apply(position, settings);
			for( RecognitionParameter parameter : PROFILE ) {
				assertEquals(settings.getValue(parameter), relaxed.getValue(parameter), position + " " + parameter);
			}
		}
		for( ArcadeLeniencyPosition position : ArcadeLeniencyPosition.values() ) {
			RecognitionSettings relaxed = ArcadeLeniency.apply(position, settings);
			for( RecognitionParameter parameter : PROFILE ) {
				assertEquals(settings.getValue(parameter), relaxed.getValue(parameter), position + " " + parameter);
			}
		}
		assertFalse(FollowLeniency.relaxedParameters().contains(RecognitionParameters.CHORD_PROFILE_NAMES));
		assertFalse(ArcadeLeniency.relaxedParameters().contains(RecognitionParameters.CHORD_PROFILE_NAMES));
	}
}
