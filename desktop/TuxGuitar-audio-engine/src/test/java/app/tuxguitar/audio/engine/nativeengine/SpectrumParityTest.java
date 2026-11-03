package app.tuxguitar.audio.engine.nativeengine;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.analysis.PitchCandidate;
import app.tuxguitar.audio.engine.analysis.SpectrumFrame;

/**
 * The native polyphonic spectrum gives the Java one (spec 023 T020, {@code PolyphonicAnalyzer} and {@code HarmonicComb}): band dB, salience, candidates
 * and peaks within 1e-4 relative, the same candidate set on every frame.
 */
public class SpectrumParityTest {

	static final int RECORDINGS = 24;

	@BeforeAll
	public static void library() {
		ParityRecordings.requireNativeLibrary();
	}

	@Test
	public void everyFrameHasTheSameSpectrum() throws Exception {
		ParityEngines.Request request = new ParityEngines.Request();
		request.spectrum = true;
		for (ParityRecordings.Recording recording : ParityRecordings.first(RECORDINGS)) {
			List<AnalysisFrame> java = ParityEngines.java(recording.mono, recording.rate, request);
			List<AnalysisFrame> nativeFrames = ParityEngines.nativeFrames(recording.mono, recording.rate, request);
			Assertions.assertEquals(java.size(), nativeFrames.size(), recording.toString());
			for (int i = 0; i < java.size(); i++) {
				assertSame(recording + " frame " + i, java.get(i).getSpectrum(), nativeFrames.get(i).getSpectrum());
			}
		}
	}

	static void assertSame(String label, SpectrumFrame a, SpectrumFrame b) {
		Assertions.assertNotNull(a, label);
		Assertions.assertNotNull(b, label);
		Assertions.assertEquals(a.getBinHz(), b.getBinHz(), 1e-12, label);
		Assertions.assertEquals(a.getBandCount(), b.getBandCount(), label);
		for (int midi = a.getMinBandMidi(); midi < a.getMinBandMidi() + a.getBandCount(); midi++) {
			// levels in dB: 1e-4 relative of the amplitude is under 0.001 dB
			double da = a.getBandDb(midi);
			double db = b.getBandDb(midi);
			Assertions.assertTrue(da == db || Math.abs(da - db) <= 0.001 || (da < -200 && db < -200), label + " band " + midi + ": " + da + " / " + db);
		}
		double maxSalience = 0.0;
		for (int midi = a.getMinSalienceMidi(); midi < a.getMinSalienceMidi() + a.getSalienceCount(); midi++) {
			maxSalience = Math.max(maxSalience, a.getSalience(midi));
		}
		for (int midi = a.getMinSalienceMidi(); midi < a.getMinSalienceMidi() + a.getSalienceCount(); midi++) {
			assertClose(label + " salience " + midi, a.getSalience(midi), b.getSalience(midi), maxSalience);
		}
		assertSameCandidates(label, a.getCandidates(), b.getCandidates());
		Assertions.assertEquals(a.getPeakLength(), b.getPeakLength(), label);
		double maxPeak = 0.0;
		for (int k = 0; k < a.getPeakLength(); k++) {
			maxPeak = Math.max(maxPeak, a.getPeak(k));
		}
		for (int k = 0; k < a.getPeakLength(); k++) {
			assertClose(label + " peak " + k, a.getPeak(k), b.getPeak(k), maxPeak);
		}
	}

	static void assertSameCandidates(String label, List<PitchCandidate> a, List<PitchCandidate> b) {
		StringBuilder expected = new StringBuilder();
		StringBuilder actual = new StringBuilder();
		for (PitchCandidate c : a) {
			expected.append(c.getMidi()).append(' ');
		}
		for (PitchCandidate c : b) {
			actual.append(c.getMidi()).append(' ');
		}
		Assertions.assertEquals(expected.toString(), actual.toString(), label + " candidates");
		double top = a.isEmpty() ? 0.0 : a.get(0).getSalience();
		for (int i = 0; i < a.size(); i++) {
			assertClose(label + " candidate " + i + " salience", a.get(i).getSalience(), b.get(i).getSalience(), top);
			assertClose(label + " candidate " + i + " relative", a.get(i).getRelativeSalience(), b.get(i).getRelativeSalience(), 1.0);
			double la = a.get(i).getLevelDb();
			double lb = b.get(i).getLevelDb();
			Assertions.assertTrue(la == lb || Math.abs(la - lb) <= 0.001, label + " candidate " + i + " level " + la + " / " + lb);
		}
	}

	/** 1e-4 relative, against the value or, for the values near zero, against 1e-3 of the frame's largest. */
	static void assertClose(String label, double expected, double actual, double scale) {
		double tolerance = 1e-4 * Math.max(Math.abs(expected), 1e-3 * scale);
		if (!(Math.abs(expected - actual) <= tolerance) && !(expected == actual)) {
			Assertions.fail(label + ": Java " + expected + " native " + actual);
		}
	}
}
