package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeLeniency;
import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeLeniencyPosition;
import app.tuxguitar.audio.engine.settings.RecognitionParameter;
import app.tuxguitar.audio.engine.settings.RecognitionParameter.Section;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;

/**
 * Each leniency value takes effect (spec 026, T025, SC-001): every value of the four sections at one end of its range changes the settings a Follow or
 * Arcade run is made with at its position, or the width of Arcade's window for a chosen width of 40, 75 or 120 ms.
 */
public class LeniencyTakesEffectTest {

	@Test
	public void everyLeniencyValueChangesARun() {
		String recommended = decisions(RecognitionSettings.defaults());
		List<String> withoutEffect = new ArrayList<String>();
		for( Section section : new Section[] { Section.LENIENCY_1, Section.LENIENCY_2, Section.LENIENCY_3, Section.LENIENCY_4 } ) {
			for( RecognitionParameter parameter : RecognitionParameters.of(section) ) {
				RecognitionParameter.Number number = (RecognitionParameter.Number) parameter;
				boolean changed = false;
				for( double end : new double[] { number.getMinimum(), number.getMaximum() } ) {
					changed |= !decisions(RecognitionSettings.defaults().with(number, end)).equals(recommended);
				}
				if( !changed ) {
					withoutEffect.add(number.getKey());
				}
			}
		}
		assertEquals(new ArrayList<String>(), withoutEffect, "values whose ends change no run");
	}

	private static String decisions(RecognitionSettings settings) {
		StringBuilder s = new StringBuilder();
		for( FollowLeniencyPosition position : FollowLeniencyPosition.values() ) {
			s.append(relaxed(FollowLeniency.apply(position, settings))).append(';');
		}
		for( ArcadeLeniencyPosition position : ArcadeLeniencyPosition.values() ) {
			s.append(relaxed(ArcadeLeniency.apply(position, settings)));
			for( int chosen : new int[] { 40, 75, 120 } ) {
				s.append(' ').append(ArcadeLeniency.windowMillis(position, chosen, settings));
			}
			s.append(';');
		}
		return s.toString();
	}

	private static String relaxed(RecognitionSettings s) {
		StringBuilder text = new StringBuilder();
		for( RecognitionParameter.Number row : new RecognitionParameter.Number[] { RecognitionParameters.NOTE_ACCEPT, RecognitionParameters.NOTE_CONFIDENCE,
				RecognitionParameters.NOTE_STABILITY, RecognitionParameters.CHORD_EVIDENCE, RecognitionParameters.CHORD_MIN_PARTIALS,
				RecognitionParameters.CHORD_CONFIRM } ) {
			text.append(s.get(row)).append(' ');
		}
		return text.toString();
	}
}
