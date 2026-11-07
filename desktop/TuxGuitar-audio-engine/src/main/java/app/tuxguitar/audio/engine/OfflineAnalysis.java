package app.tuxguitar.audio.engine;

import java.util.ArrayList;
import java.util.List;

import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.analysis.ArticulationDetector;
import app.tuxguitar.audio.engine.analysis.AttackAnalyzer;
import app.tuxguitar.audio.engine.analysis.AttackEvidence;
import app.tuxguitar.audio.engine.analysis.LevelAnalyzer;
import app.tuxguitar.audio.engine.analysis.LevelMeasurement;
import app.tuxguitar.audio.engine.analysis.PitchEstimate;
import app.tuxguitar.audio.engine.analysis.PitchEstimator;
import app.tuxguitar.audio.engine.analysis.PolyphonicAnalyzer;
import app.tuxguitar.audio.engine.analysis.SpectrumFrame;
import app.tuxguitar.audio.engine.nativeengine.FrameRecords;
import app.tuxguitar.audio.engine.nativeengine.NativeParameters;
import app.tuxguitar.audio.engine.nativeengine.NativeRecognitionLibrary;
import app.tuxguitar.audio.engine.settings.PitchEngineCatalog;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;

/**
 * Runs recorded audio through the same analysis stages as the live analysis worker (level, pitch,
 * attack, polyphonic spectrum, attack evidence), hop by hop, for tests and benchmarks. Deterministic.
 * The audio is only read, never stored or written.
 */
public final class OfflineAnalysis {

	public static final int WINDOW = 2048;
	public static final int HOP = 256;

	private OfflineAnalysis() {
	}

	/**
	 * @param mono samples in [-1, 1]
	 * @param spectrum add the polyphonic spectrum to every frame (chord checks)
	 * @param attackCandidates the pitches the attack evidence is computed for, or null for none
	 */
	public static List<AnalysisFrame> analyze(float[] mono, int sampleRate, RecognitionSettings settings, boolean spectrum, int[] attackCandidates) {
		return analyze(mono, sampleRate, settings, spectrum, attackCandidates, null);
	}

	/**
	 * Like the other overload, also giving every hop to {@code hops} after its {@code configure(sampleRate)} (spec 010: the same call as the live engine,
	 * so the tests run the production loop; there is no input front end offline, the hops are the samples as given). The frames are the same with or
	 * without a listener.
	 */
	public static List<AnalysisFrame> analyze(float[] mono, int sampleRate, RecognitionSettings settings, boolean spectrum, int[] attackCandidates,
			app.tuxguitar.audio.engine.analysis.HopListener hops) {
		return analyze(mono, sampleRate, settings, spectrum, attackCandidates, hops, null, null);
	}

	/**
	 * Like the other overloads, with the chord a judge expects (spec 023, US3): the native engine then carries its evidence for that chord in every frame, as
	 * it does live once the expected chord is set, so a chord check reads the native evidence offline too; the Java engine has no such evidence and ignores
	 * it (its chord check reads the spectrum).
	 *
	 * @param chordRequired the required pitches of the expected chord, or null for none
	 * @param chordTolerated the pitches it tolerates, or null for none
	 */
	public static List<AnalysisFrame> analyze(float[] mono, int sampleRate, RecognitionSettings settings, boolean spectrum, int[] attackCandidates,
			int[] chordRequired, int[] chordTolerated) {
		return analyze(mono, sampleRate, settings, spectrum, attackCandidates, null, chordRequired, chordTolerated);
	}

	private static List<AnalysisFrame> analyze(float[] mono, int sampleRate, RecognitionSettings settings, boolean spectrum, int[] attackCandidates,
			app.tuxguitar.audio.engine.analysis.HopListener hops, int[] chordRequired, int[] chordTolerated) {
		if( PitchEngineCatalog.resolveKind(settings) == PitchEngineCatalog.Kind.NATIVE ) {
			return analyzeNative(mono, sampleRate, settings, spectrum, attackCandidates, hops, chordRequired, chordTolerated);
		}
		LevelAnalyzer levels = AudioEngine.levelAnalyzer(settings);
		ArticulationDetector articulation = AudioEngine.articulationDetector(settings);
		PitchEstimator estimator = PitchEngineCatalog.resolve(settings.get(RecognitionParameters.ENGINE_PITCH)).create(sampleRate, settings);
		PolyphonicAnalyzer polyphonic = (spectrum ? new PolyphonicAnalyzer(PolyphonicAnalyzer.Parameters.of(settings), sampleRate) : null);
		AttackAnalyzer attacks = (attackCandidates != null && attackCandidates.length > 0
				? new AttackAnalyzer(AttackAnalyzer.Parameters.of(settings), sampleRate, HOP, attackCandidates) : null);
		if( hops != null ) {
			hops.configure(sampleRate);
		}
		float[] window = new float[WINDOW];
		float[] hop = new float[HOP];
		int filled = 0;
		long processed = 0;
		List<AnalysisFrame> frames = new ArrayList<AnalysisFrame>();
		for( int offset = 0 ; offset + HOP <= mono.length ; offset += HOP ) {
			System.arraycopy(mono, offset, hop, 0, HOP);
			if( hops != null ) {
				hops.hop(hop, 0, HOP);
			}
			System.arraycopy(window, HOP, window, 0, WINDOW - HOP);
			System.arraycopy(hop, 0, window, WINDOW - HOP, HOP);
			filled = Math.min(WINDOW, filled + HOP);
			processed += HOP;
			if( polyphonic != null ) {
				polyphonic.push(hop, 0, HOP);
			}
			AttackEvidence evidence = null;
			if( attacks != null ) {
				attacks.push(hop, 0, HOP);
				evidence = attacks.analyze();
			}
			if( filled < WINDOW ) {
				continue;
			}
			LevelMeasurement level = levels.analyze(window);
			PitchEstimate pitch = estimator.estimate(window);
			boolean onset = articulation.process(window);
			SpectrumFrame frameSpectrum = (polyphonic != null ? polyphonic.analyze() : null);
			frames.add(new AnalysisFrame(processed * 1_000_000_000L / sampleRate, level.getRmsDbfs(), level.getPeakDbfs(), level.getClippedRatio(), level.getLevelState(),
					pitch.isPitched() ? pitch.getF0Hz() : Double.NaN, pitch.getProbability(), onset, frameSpectrum, null, null, evidence));
		}
		return frames;
	}

	/** The native engine (spec 023, contract §3): the whole recording in one call, read back record by record; the listener gets the same hops. */
	private static List<AnalysisFrame> analyzeNative(float[] mono, int sampleRate, RecognitionSettings settings, boolean spectrum, int[] attackCandidates,
			app.tuxguitar.audio.engine.analysis.HopListener hops, int[] chordRequired, int[] chordTolerated) {
		if( hops != null ) {
			hops.configure(sampleRate);
			float[] hop = new float[HOP];
			for( int offset = 0 ; offset + HOP <= mono.length ; offset += HOP ) {
				System.arraycopy(mono, offset, hop, 0, HOP);
				hops.hop(hop, 0, HOP);
			}
		}
		float[] records = NativeRecognitionLibrary.analyzeTuned(mono, sampleRate, NativeParameters.of(settings), NativeParameters.tuning(settings), spectrum,
				attackCandidates, null, chordRequired, chordTolerated);
		List<AnalysisFrame> frames = new ArrayList<AnalysisFrame>();
		PolyphonicAnalyzer.Parameters polyphonic = PolyphonicAnalyzer.Parameters.of(settings);
		int offset = 0;
		while( offset < records.length ) {
			frames.add(FrameRecords.read(records, offset, polyphonic));
			offset += FrameRecords.length(records, offset);
		}
		return frames;
	}
}
