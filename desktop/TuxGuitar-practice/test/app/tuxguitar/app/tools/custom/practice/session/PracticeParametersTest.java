package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.pitch.TolerancePolicy;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;

/** Propagation of the recognition settings to practice notes and chords (task T102). */
public class PracticeParametersTest {

	@Test
	public void recommendedSettingsReproduceTheFormerPracticeValuesExactly() {
		PracticeParameters p = PracticeParameters.of(RecognitionSettings.defaults());

		assertEquals(0.80, p.noteInterpreter().getConfidenceThreshold());
		TolerancePolicy tolerance = p.tolerance();
		assertEquals(5.0, tolerance.getTunerInTuneCents());
		assertEquals(35.0, tolerance.getPracticeAcceptCents());
		// formerly STABILITY_HOPS (20) × the hop, and DECISION_DELAY_HOPS (13) × the hop
		assertEquals(20 * (256L * 1_000_000_000L / 48000L), p.stabilityNanos());
		assertEquals(13 * (256L * 1_000_000_000L / 48000L), p.articulationLatencyNanos());
		ChordMatcher.Parameters chord = p.chordParameters();
		ChordMatcher.Parameters former = ChordMatcher.Parameters.defaults();
		assertEquals(former.minClearPartials, chord.minClearPartials);
		assertEquals(former.minClearFraction, chord.minClearFraction);
		assertEquals(former.stableHops, chord.stableHops);
		assertEquals(former.windowNanos, chord.windowNanos);
	}

	@Test
	public void chosenValuesReachNotesAndChords() {
		RecognitionSettings s = RecognitionSettings.defaults()
				.with(RecognitionParameters.NOTE_CONFIDENCE, 0.9)
				.with(RecognitionParameters.NOTE_ACCEPT, 20)
				.with(RecognitionParameters.TUNER_IN_TUNE, 3)
				.with(RecognitionParameters.NOTE_STABILITY, 300)
				.with(RecognitionParameters.CHORD_EVIDENCE, 0.5)
				.with(RecognitionParameters.CHORD_WINDOW, 1500)
				.with(RecognitionParameters.CHORD_MIN_PARTIALS, 3)
				.with(RecognitionParameters.CHORD_CONFIRM, 150);
		PracticeParameters p = PracticeParameters.of(s);

		assertEquals(0.9, p.noteInterpreter().getConfidenceThreshold());
		assertEquals(3.0, p.tolerance().getTunerInTuneCents());
		assertEquals(20.0, p.tolerance().getPracticeAcceptCents());
		assertEquals(56 * PracticeParameters.HOP_NANOS, p.stabilityNanos());
		assertEquals(3, p.chordParameters().minClearPartials);
		assertEquals(0.5, p.chordParameters().minClearFraction);
		assertEquals(28, p.chordParameters().stableHops);
		assertEquals(1_500_000_000L, p.chordParameters().windowNanos);
	}

	@Test
	public void extremeAndIncoherentSettingsStillBuildValidConsumers() {
		RecognitionSettings s = RecognitionSettings.defaults();
		s = s.with(RecognitionParameters.TUNER_IN_TUNE, 1e9).with(RecognitionParameters.NOTE_ACCEPT, -1e9)
				.with(RecognitionParameters.NOTE_CONFIDENCE, Double.NaN).with(RecognitionParameters.NOTE_STABILITY, Double.POSITIVE_INFINITY);
		PracticeParameters p = PracticeParameters.of(s);
		// in-tune 20 raises the acceptance to 20; TolerancePolicy would reject accept < in-tune
		assertEquals(20.0, p.tolerance().getPracticeAcceptCents());
		assertEquals(0.80, p.noteInterpreter().getConfidenceThreshold());
		assertEquals(20 * PracticeParameters.HOP_NANOS, p.stabilityNanos());
	}
}
