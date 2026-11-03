package app.tuxguitar.audio.engine.nativeengine;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.analysis.AnalysisFrame;

/**
 * The native YIN gives the Java figures (spec 023 T018): within 1 cent when both are pitched, the same pitched/unpitched decision on at least 99.9 % of
 * the frames, the same confidence within 1e-4; on the parity recordings and on synthetic plucks of the lowest string of each tuning (5-string bass B0
 * included) at 44.1, 48, 88.2 and 96 kHz.
 */
public class PitchParityTest {

	/** The lowest strings of the practice's tunings: guitar E2, drop D, drop C, B standard, 7-string B1, bass E1, 5-string bass B0, and some highs. */
	private static final int[] PITCHES = { 40, 38, 36, 35, 23, 28, 23, 33, 45, 52, 59, 64, 76, 88 };
	private static final int[] RATES = { 44100, 48000, 88200, 96000 };

	private int frames;
	private int decisionsDiffer;

	@BeforeAll
	public static void library() {
		ParityRecordings.requireNativeLibrary();
	}

	@Test
	public void theRecordingsGiveTheSamePitches() throws Exception {
		for (ParityRecordings.Recording recording : ParityRecordings.all()) {
			compare(recording.toString(), recording.mono, recording.rate);
		}
		assertDecisions();
	}

	@Test
	public void syntheticPlucksAtEveryRate() {
		for (int rate : RATES) {
			for (int midi : PITCHES) {
				compare("pluck " + midi + " at " + rate, pluck(midi, rate), rate);
			}
		}
		assertDecisions();
	}

	private void compare(String label, float[] mono, int rate) {
		ParityEngines.Request request = new ParityEngines.Request();
		List<AnalysisFrame> java = ParityEngines.java(mono, rate, request);
		List<AnalysisFrame> nativeFrames = ParityEngines.nativeFrames(mono, rate, request);
		Assertions.assertEquals(java.size(), nativeFrames.size(), label);
		for (int i = 0; i < java.size(); i++) {
			AnalysisFrame a = java.get(i);
			AnalysisFrame b = nativeFrames.get(i);
			this.frames++;
			boolean pitchedA = !Double.isNaN(a.getF0Hz());
			boolean pitchedB = !Double.isNaN(b.getF0Hz());
			if (pitchedA != pitchedB) {
				this.decisionsDiffer++;
				continue;
			}
			if (pitchedA) {
				double cents = 1200.0 * Math.abs(Math.log(b.getF0Hz() / a.getF0Hz()) / Math.log(2.0));
				Assertions.assertTrue(cents <= 1.0, label + " frame " + i + ": " + a.getF0Hz() + " Hz / " + b.getF0Hz() + " Hz (" + cents + " cents)");
			}
			Assertions.assertEquals(a.getConfidence(), b.getConfidence(), 1e-4, label + " frame " + i + " confidence");
		}
	}

	private void assertDecisions() {
		Assertions.assertTrue(this.frames > 0, "no frame compared");
		Assertions.assertTrue(this.decisionsDiffer <= 0.001 * this.frames, this.decisionsDiffer + " of " + this.frames + " frames differ pitched/unpitched");
	}

	/** A plucked string: six partials with a decay, 1.5 s. */
	static float[] pluck(int midi, int rate) {
		double f = 440.0 * Math.pow(2.0, (midi - 69) / 12.0);
		float[] samples = new float[(int) (1.5 * rate)];
		for (int i = 0; i < samples.length; i++) {
			double t = i / (double) rate;
			double value = 0.0;
			for (int h = 1; h <= 6; h++) {
				value += 0.3 / h * Math.exp(-t * (1.5 + h)) * Math.sin(2 * Math.PI * f * h * t);
			}
			samples[i] = (float) value;
		}
		return samples;
	}
}
