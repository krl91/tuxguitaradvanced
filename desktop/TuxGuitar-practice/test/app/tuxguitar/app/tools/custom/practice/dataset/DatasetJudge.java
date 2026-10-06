package app.tuxguitar.app.tools.custom.practice.dataset;

import java.util.ArrayList;
import java.util.List;

import app.tuxguitar.app.tools.custom.practice.model.PracticeSequence;
import app.tuxguitar.app.tools.custom.practice.replay.RecordedAudio;
import app.tuxguitar.app.tools.custom.practice.model.PracticeTrackCandidates;
import app.tuxguitar.app.tools.custom.practice.model.PracticeTrackSelection;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.session.AttackCandidates;
import app.tuxguitar.app.tools.custom.practice.session.ChordMatcher;
import app.tuxguitar.app.tools.custom.practice.session.PolyphonicAcceptance;
import app.tuxguitar.app.tools.custom.practice.session.PracticeCommand;
import app.tuxguitar.app.tools.custom.practice.session.PracticeParameters;
import app.tuxguitar.app.tools.custom.practice.session.PracticeSession;
import app.tuxguitar.audio.engine.OfflineAnalysis;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.audio.engine.session.PracticeTunerContext;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.util.TGContext;

/** Asks "would Practice, expecting these pitches, accept this recording?" with the production rules. */
public final class DatasetJudge {

	private static final int[] OPEN_STRINGS = { 64, 59, 55, 50, 45, 40 };
	private static final int MAX_FRET = 24;

	private DatasetJudge() {
	}

	/** Strings (1 = high E) for each pitch, each on its own string, or null when a standard 6-string guitar cannot play them together. */
	public static int[] strings(int[] pitches) {
		int[] chosen = new int[pitches.length];
		boolean[] used = new boolean[OPEN_STRINGS.length];
		return assign(pitches, 0, chosen, used) ? chosen : null;
	}

	private static boolean assign(int[] pitches, int index, int[] chosen, boolean[] used) {
		if( index == pitches.length ) {
			return true;
		}
		// the lowest fret first: the notes of a chord lie where a hand would put them
		for( int fret = 0 ; fret <= MAX_FRET ; fret ++ ) {
			for( int s = 0 ; s < OPEN_STRINGS.length ; s ++ ) {
				if( !used[s] && OPEN_STRINGS[s] + fret == pitches[index] ) {
					used[s] = true;
					chosen[index] = s + 1;
					if( assign(pitches, index + 1, chosen, used) ) {
						return true;
					}
					used[s] = false;
				}
			}
		}
		return false;
	}

	/** @return true when a session expecting {@code expected} accepts the recording; expected must be playable (see {@link #strings}) */
	static boolean accepts(RecordedAudio audio, int[] expected) {
		int[] strings = strings(expected);
		SongFixture fixture = new SongFixture(1);
		for( int i = 0 ; i < expected.length ; i ++ ) {
			fixture.quarter(0, 0, strings[i], expected[i] - OPEN_STRINGS[strings[i] - 1]);
		}
		PracticeSequence sequence = fixture.sequence();
		PracticeTrackSelection selection = new PracticeTrackSelection(fixture.song,
				PracticeTrackCandidates.evaluate(fixture.song, fixture.track()).getPreselected(), PracticeTunerContext.getInstance(new TGContext()));
		selection.accept();
		PracticeParameters parameters = PracticeParameters.of(RecognitionSettings.defaults());
		PracticeSession session = new PracticeSession(selection, sequence, parameters.noteInterpreter(), parameters.tolerance(), parameters.stabilityNanos(),
				parameters.articulationLatencyNanos(), new ChordMatcher(parameters.chordParameters()), null);
		session.setPolyphonicAcceptance(new PolyphonicAcceptance(PolyphonicAcceptance.Parameters.defaults()));
		session.setAssessment(new InputAssessment(InputAssessment.Status.SUITABLE, InputAssessment.Reason.NONE));
		session.onCommand(PracticeCommand.START);
		// the recording starts on the note itself; a learner presses Start, then plays: half a second of silence first
		float[] samples = new float[audio.samples.length + audio.sampleRate / 2];
		System.arraycopy(audio.samples, 0, samples, audio.sampleRate / 2, audio.samples.length);
		List<AnalysisFrame> frames = OfflineAnalysis.analyze(samples, audio.sampleRate, RecognitionSettings.defaults(), expected.length > 1, AttackCandidates.of(sequence));
		for( AnalysisFrame frame : frames ) {
			if( session.onFrame(frame).getAcceptedCount() > 0 ) {
				return true;
			}
		}
		return false;
	}

	/**
	 * When a session expecting {@code expected}, each pitch on the string given (1 = high E; the fret follows from it), accepts what is
	 * heard in {@code samples}: the time of the acceptance in seconds from the start of the samples, or NaN when it never accepts. The
	 * samples are fed as they are (no silence is added): the excerpt itself carries the sound that rings before the strum.
	 */
	static double acceptanceSeconds(float[] samples, int sampleRate, int[] expected, int[] strings) {
		return acceptanceSeconds(samples, sampleRate, expected, strings, PolyphonicAcceptance.Parameters.defaults());
	}

	/** Like {@link #acceptanceSeconds(float[], int, int[], int[])} with the given acceptance parameters (to measure what a setting would change). */
	static double acceptanceSeconds(float[] samples, int sampleRate, int[] expected, int[] strings, PolyphonicAcceptance.Parameters acceptance) {
		SongFixture fixture = new SongFixture(1);
		for( int i = 0 ; i < expected.length ; i ++ ) {
			fixture.quarter(0, 0, strings[i], expected[i] - OPEN_STRINGS[strings[i] - 1]);
		}
		PracticeSequence sequence = fixture.sequence();
		PracticeTrackSelection selection = new PracticeTrackSelection(fixture.song,
				PracticeTrackCandidates.evaluate(fixture.song, fixture.track()).getPreselected(), PracticeTunerContext.getInstance(new TGContext()));
		selection.accept();
		PracticeParameters parameters = PracticeParameters.of(RecognitionSettings.defaults());
		PracticeSession session = new PracticeSession(selection, sequence, parameters.noteInterpreter(), parameters.tolerance(), parameters.stabilityNanos(),
				parameters.articulationLatencyNanos(), new ChordMatcher(parameters.chordParameters()), null);
		session.setPolyphonicAcceptance(new PolyphonicAcceptance(acceptance));
		session.setAssessment(new InputAssessment(InputAssessment.Status.SUITABLE, InputAssessment.Reason.NONE));
		session.onCommand(PracticeCommand.START);
		List<AnalysisFrame> frames = OfflineAnalysis.analyze(samples, sampleRate, RecognitionSettings.defaults(), expected.length > 1, AttackCandidates.of(sequence));
		for( AnalysisFrame frame : frames ) {
			if( session.onFrame(frame).getAcceptedCount() > 0 ) {
				return frame.getTimestampNanos() / 1e9;
			}
		}
		return Double.NaN;
	}

	/**
	 * Practice expecting {@code steps} one after the other (one note or chord per step) over {@code audio}:
	 * how many of them it accepts, in order. The audio is fed as one stream, so a step is judged while the sound of the previous one still rings.
	 */
	public static int acceptedSteps(RecordedAudio audio, int[][] steps) {
		SongFixture fixture = new SongFixture(1);
		for( int step = 0 ; step < steps.length ; step ++ ) {
			int[] strings = strings(steps[step]);
			for( int i = 0 ; i < steps[step].length ; i ++ ) {
				fixture.quarter(0, step, strings[i], steps[step][i] - OPEN_STRINGS[strings[i] - 1]);
			}
		}
		PracticeSequence sequence = fixture.sequence();
		PracticeTrackSelection selection = new PracticeTrackSelection(fixture.song,
				PracticeTrackCandidates.evaluate(fixture.song, fixture.track()).getPreselected(), PracticeTunerContext.getInstance(new TGContext()));
		selection.accept();
		PracticeParameters parameters = PracticeParameters.of(RecognitionSettings.defaults());
		PracticeSession session = new PracticeSession(selection, sequence, parameters.noteInterpreter(), parameters.tolerance(), parameters.stabilityNanos(),
				parameters.articulationLatencyNanos(), new ChordMatcher(parameters.chordParameters()), null);
		session.setPolyphonicAcceptance(new PolyphonicAcceptance(PolyphonicAcceptance.Parameters.defaults()));
		session.setAssessment(new InputAssessment(InputAssessment.Status.SUITABLE, InputAssessment.Reason.NONE));
		session.onCommand(PracticeCommand.START);
		float[] samples = new float[audio.samples.length + audio.sampleRate / 2];
		System.arraycopy(audio.samples, 0, samples, audio.sampleRate / 2, audio.samples.length);
		List<AnalysisFrame> frames = OfflineAnalysis.analyze(samples, audio.sampleRate, RecognitionSettings.defaults(), false, AttackCandidates.of(sequence));
		int accepted = 0;
		for( AnalysisFrame frame : frames ) {
			accepted = Math.max(accepted, session.onFrame(frame).getAcceptedCount());
		}
		return accepted;
	}

	/**
	 * Like {@link #acceptedSteps}, with the time of each acceptance: seconds from the start of {@code audio} (a second of silence is added
	 * before it, as for the other judgements), in the order of the steps; NaN for a step that was never accepted in its turn.
	 */
	public static double[] acceptanceTimes(RecordedAudio audio, int[][] steps) {
		return acceptanceTimes(audio, steps, PolyphonicAcceptance.Parameters.defaults());
	}

	/** Like {@link #acceptanceTimes(RecordedAudio, int[][])} with the given acceptance parameters (to measure what a setting would change). */
	static double[] acceptanceTimes(RecordedAudio audio, int[][] steps, PolyphonicAcceptance.Parameters acceptance) {
		SongFixture fixture = new SongFixture(1);
		for( int step = 0 ; step < steps.length ; step ++ ) {
			int[] strings = strings(steps[step]);
			for( int i = 0 ; i < steps[step].length ; i ++ ) {
				fixture.quarter(0, step, strings[i], steps[step][i] - OPEN_STRINGS[strings[i] - 1]);
			}
		}
		PracticeSequence sequence = fixture.sequence();
		PracticeTrackSelection selection = new PracticeTrackSelection(fixture.song,
				PracticeTrackCandidates.evaluate(fixture.song, fixture.track()).getPreselected(), PracticeTunerContext.getInstance(new TGContext()));
		selection.accept();
		PracticeParameters parameters = PracticeParameters.of(RecognitionSettings.defaults());
		PracticeSession session = new PracticeSession(selection, sequence, parameters.noteInterpreter(), parameters.tolerance(), parameters.stabilityNanos(),
				parameters.articulationLatencyNanos(), new ChordMatcher(parameters.chordParameters()), null);
		session.setPolyphonicAcceptance(new PolyphonicAcceptance(acceptance));
		session.setAssessment(new InputAssessment(InputAssessment.Status.SUITABLE, InputAssessment.Reason.NONE));
		session.onCommand(PracticeCommand.START);
		int pad = audio.sampleRate / 2;
		float[] samples = new float[audio.samples.length + pad];
		System.arraycopy(audio.samples, 0, samples, pad, audio.samples.length);
		List<AnalysisFrame> frames = OfflineAnalysis.analyze(samples, audio.sampleRate, RecognitionSettings.defaults(), false, AttackCandidates.of(sequence));
		double[] times = new double[steps.length];
		java.util.Arrays.fill(times, Double.NaN);
		int accepted = 0;
		for( AnalysisFrame frame : frames ) {
			int now = session.onFrame(frame).getAcceptedCount();
			while( accepted < now && accepted < times.length ) {
				times[accepted ++] = frame.getTimestampNanos() / 1e9 - 0.5;
			}
		}
		return times;
	}

	static List<Integer> list(int[] values) {
		List<Integer> result = new ArrayList<Integer>();
		for( int value : values ) {
			result.add(value);
		}
		return result;
	}
}
