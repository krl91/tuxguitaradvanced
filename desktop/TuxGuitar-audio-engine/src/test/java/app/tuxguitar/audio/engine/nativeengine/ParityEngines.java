package app.tuxguitar.audio.engine.nativeengine;

import java.util.ArrayList;
import java.util.List;

import app.tuxguitar.audio.engine.AudioEngine;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.analysis.ArticulationDetector;
import app.tuxguitar.audio.engine.analysis.AttackAnalyzer;
import app.tuxguitar.audio.engine.analysis.AttackEvidence;
import app.tuxguitar.audio.engine.analysis.ChordEvidence;
import app.tuxguitar.audio.engine.analysis.LevelAnalyzer;
import app.tuxguitar.audio.engine.analysis.LevelMeasurement;
import app.tuxguitar.audio.engine.analysis.PitchEstimate;
import app.tuxguitar.audio.engine.analysis.PitchEstimator;
import app.tuxguitar.audio.engine.analysis.PolyphonicAnalyzer;
import app.tuxguitar.audio.engine.analysis.SpectrumFrame;
import app.tuxguitar.audio.engine.analysis.TarsosFastYinEstimator;
import app.tuxguitar.audio.engine.recognition.ExpectedPitchVerifier;
import app.tuxguitar.audio.engine.recognition.TargetEvidence;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;

/**
 * Both engines on one recording, for the parity tests (spec 023): the Java side is today's offline loop of {@code OfflineAnalysis} written out here (so
 * it stays Java whatever engine the run selects), with the target and chord evidence computed as the live {@code AnalysisService} does; the native side is
 * {@link NativeRecognitionLibrary#analyze}, read back through {@link FrameRecords}.
 */
public final class ParityEngines {

	static final int WINDOW = 2048;
	static final int HOP = 256;

	/** What both engines are asked. */
	public static final class Request {
		public RecognitionSettings settings = RecognitionSettings.defaults();
		public boolean spectrum;
		public int[] attackCandidates;
		public int[] expectedPitches;
		public int[] chordRequired;
		public int[] chordTolerated = new int[0];
		public boolean fastOnset;

		float[] parameters() {
			float[] p = NativeParameters.of(this.settings);
			return this.fastOnset ? NativeParameters.withFastOnset(p) : p;
		}
	}

	private ParityEngines() {
	}

	public static List<AnalysisFrame> java(float[] mono, int rate, Request request) {
		LevelAnalyzer levels = AudioEngine.levelAnalyzer(request.settings);
		ArticulationDetector base = AudioEngine.articulationDetector(request.settings);
		ArticulationDetector articulation = (request.fastOnset ? ArticulationDetector.fast(base.getMinLevelDb(), base.getGateDb()) : base);
		PitchEstimator estimator = new TarsosFastYinEstimator(rate, TarsosFastYinEstimator.DEFAULT_WINDOW_SIZE, TarsosFastYinEstimator.DEFAULT_HOP_SIZE);
		PolyphonicAnalyzer polyphonic = (request.spectrum || request.chordRequired != null ? new PolyphonicAnalyzer(PolyphonicAnalyzer.Parameters.defaults(), rate)
				: null);
		AttackAnalyzer attacks = (request.attackCandidates != null && request.attackCandidates.length > 0
				? new AttackAnalyzer(AttackAnalyzer.Parameters.defaults(), rate, HOP, request.attackCandidates) : null);
		ExpectedPitchVerifier verifier = (request.expectedPitches != null && request.expectedPitches.length > 0 ? new ExpectedPitchVerifier(rate) : null);
		float[] window = new float[WINDOW];
		float[] hop = new float[HOP];
		int filled = 0;
		long processed = 0;
		List<AnalysisFrame> frames = new ArrayList<AnalysisFrame>();
		for (int offset = 0; offset + HOP <= mono.length; offset += HOP) {
			System.arraycopy(mono, offset, hop, 0, HOP);
			System.arraycopy(window, HOP, window, 0, WINDOW - HOP);
			System.arraycopy(hop, 0, window, WINDOW - HOP, HOP);
			filled = Math.min(WINDOW, filled + HOP);
			processed += HOP;
			if (polyphonic != null) {
				polyphonic.push(hop, 0, HOP);
			}
			AttackEvidence evidence = null;
			if (attacks != null) {
				attacks.push(hop, 0, HOP);
				evidence = attacks.analyze();
			}
			if (filled < WINDOW) {
				continue;
			}
			LevelMeasurement level = levels.analyze(window);
			PitchEstimate pitch = estimator.estimate(window);
			boolean onset = articulation.process(window);
			SpectrumFrame spectrum = (polyphonic != null ? polyphonic.analyze() : null);
			TargetEvidence target = (verifier != null ? verifier.verify(window, request.expectedPitches) : null);
			ChordEvidence chord = (spectrum != null && request.chordRequired != null ? ChordEvidence.evaluate(spectrum, request.chordRequired, request.chordTolerated)
					: null);
			frames.add(new AnalysisFrame(processed * 1_000_000_000L / rate, level.getRmsDbfs(), level.getPeakDbfs(), level.getClippedRatio(), level.getLevelState(),
					pitch.isPitched() ? pitch.getF0Hz() : Double.NaN, pitch.getProbability(), onset, request.spectrum ? spectrum : null, target, null, evidence,
					AnalysisFrame.NO_CAPTURE_SAMPLE, chord));
		}
		return frames;
	}

	public static List<AnalysisFrame> nativeFrames(float[] mono, int rate, Request request) {
		float[] records = NativeRecognitionLibrary.analyze(mono, rate, request.parameters(), request.spectrum, request.attackCandidates, request.expectedPitches,
				request.chordRequired, request.chordTolerated);
		List<AnalysisFrame> frames = new ArrayList<AnalysisFrame>();
		int offset = 0;
		while (offset < records.length) {
			frames.add(FrameRecords.read(records, offset));
			offset += FrameRecords.length(records, offset);
		}
		return frames;
	}

	/** The relative difference, the absolute one under {@code floor}. */
	static double relative(double expected, double actual, double floor) {
		if (Double.isNaN(expected) || Double.isNaN(actual)) {
			return Double.isNaN(expected) && Double.isNaN(actual) ? 0.0 : Double.POSITIVE_INFINITY;
		}
		if (Double.isInfinite(expected) || Double.isInfinite(actual)) {
			return expected == actual ? 0.0 : Double.POSITIVE_INFINITY;
		}
		return Math.abs(expected - actual) / Math.max(floor, Math.abs(expected));
	}
}
