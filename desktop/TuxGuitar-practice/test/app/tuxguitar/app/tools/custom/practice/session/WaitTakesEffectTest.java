package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.model.PracticeTrackCandidates;
import app.tuxguitar.app.tools.custom.practice.model.PracticeTrackSelection;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.model.TechniqueResult;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.analysis.AttackEvidence;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.audio.engine.pitch.NoteInterpreter;
import app.tuxguitar.audio.engine.pitch.TolerancePolicy;
import app.tuxguitar.audio.engine.recognition.TargetEvidence;
import app.tuxguitar.audio.engine.session.PracticeTunerContext;
import app.tuxguitar.audio.engine.settings.RecognitionParameter;
import app.tuxguitar.audio.engine.settings.RecognitionParameter.Section;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.audio.engine.technique.BendDetector;
import app.tuxguitar.audio.engine.technique.TechniqueCatalog;
import app.tuxguitar.audio.engine.technique.VibratoDetector;
import app.tuxguitar.util.TGContext;

/**
 * Each Wait value takes effect (spec 026, T021, SC-001): on scripted analysis frames (no audio), every value of the two sections at one end of its
 * range changes what the session shows, frame by frame (the note, the count, the verdict, the speed limit, the weak input, the technique results).
 * The trials: a verdict after an acceptance, attacks just before and after an acceptance, a neighbour heard during an attack, notes played softly
 * and loudly, a note with a large and a small evidence margin, a vibrato, bends at two tempos, bends that go past their target.
 */
public class WaitTakesEffectTest {

	private static final long HOP_NANOS = 256L * 1_000_000_000L / 48000L;
	private static final long STABILITY_NANOS = 100_000_000L;
	private static final long LATENCY_NANOS = 13 * HOP_NANOS;
	private static final int D4 = 62;
	private static final int E4 = 64;
	private static final int F4 = 65;
	private static final int[] PITCHES = { 60, 62, 64, 65, 67 };
	private static final InputAssessment SUITABLE = new InputAssessment(InputAssessment.Status.SUITABLE, InputAssessment.Reason.NONE);

	private long now;
	private int frameCount;
	private PracticeSession session;
	private String shown;
	private PracticeSnapshot last;
	private StringBuilder log;

	@Test
	public void everyWaitValueChangesWhatIsShown() {
		String recommended = decisions(RecognitionSettings.defaults());
		assertTrue(recommended.contains("CORRECT"), "the recommended values accept notes: " + recommended);
		List<String> withoutEffect = new ArrayList<String>();
		for( Section section : new Section[] { Section.WAIT_1, Section.WAIT_2 } ) {
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
		assertEquals(new ArrayList<String>(), withoutEffect, "values whose ends change nothing shown");
	}

	private String decisions(RecognitionSettings settings) {
		this.log = new StringBuilder();
		verdictHold(settings);
		for( long offsetMillis : new long[] { 100, 230, -19, -60 } ) {
			attackAfterAcceptance(settings, offsetMillis);
		}
		for( double confidence : new double[] { 0.9, 0.6 } ) {
			neighbour(settings, confidence);
		}
		for( double rms : new double[] { -35.0, -20.0 } ) {
			level(settings, rms);
		}
		for( double score : new double[] { 0.7, 0.4 } ) {
			evidence(settings, score);
		}
		vibrato(settings);
		for( int tempo : new int[] { 120, 20 } ) {
			bendObserved(settings, tempo);
		}
		for( double past : new double[] { 0.3, 0.7 } ) {
			bendPastTarget(settings, past);
		}
		return this.log.toString();
	}

	// ---------------------------------------------------------------- the trials

	/** E4 accepted, then a long silence: the check mark goes when its display time is over. */
	private void verdictHold(RecognitionSettings settings) {
		start("hold", twoNotes(), settings, new TolerancePolicy(5.0, 25.0));
		pluckUntilAccepted(E4);
		silence(3_500_000_000L);
	}

	/** E4 accepted, then an attack whose transient falls {@code offsetMillis} after the acceptance, and no pitch: a speed limit or not. */
	private void attackAfterAcceptance(RecognitionSettings settings, long offsetMillis) {
		start("attack " + offsetMillis, twoNotes(), settings, new TolerancePolicy(5.0, 25.0));
		long accepted = pluckUntilAccepted(E4);
		long onset = accepted + offsetMillis * 1_000_000L + LATENCY_NANOS;
		while( this.now + HOP_NANOS < onset ) {
			frame(Double.NaN, 0.0, false, -20.0, null, null);
		}
		frame(Double.NaN, 0.0, true, -20.0, null, null);
		silence(1_000_000_000L);
	}

	/**
	 * E4 expected, judged by the attack evidence: during its attack the pitch estimate reads F4 at {@code confidence}; the evidence says E4. A
	 * neighbour read with enough confidence holds the acceptance for a while.
	 */
	private void neighbour(RecognitionSettings settings, double confidence) {
		start("neighbour " + confidence, twoNotes(), settings, new TolerancePolicy(5.0, 25.0));
		this.session.setPolyphonicAcceptance(new PolyphonicAcceptance(PolyphonicAcceptance.Parameters.of(settings), settings));
		// past the time ignored after the start
		for( int k = 0 ; k < 50 ; k ++ ) {
			frame(Double.NaN, 0.0, false, -50.0, new AttackEvidence(PITCHES, equal(0.0015), 0.05), null);
		}
		double[] growth = growth(E4);
		for( int k = 0 ; k < 120 ; k ++ ) {
			double hz = (k < 10 ? hz(F4) : Double.NaN);
			frame(hz, k < 10 ? confidence : 0.0, false, -20.0,
					new AttackEvidence(PITCHES, note(E4, 0.2 * Math.pow(0.98, k)), k < 3 ? 0.8 : 0.3, growth, BIN_HZ), null);
		}
	}

	/** Four notes plucked at {@code rms} dBFS: the input is said too weak for fast recognition, or not. */
	private void level(RecognitionSettings settings, double rms) {
		SongFixture fixture = new SongFixture(3);
		for( int m = 0 ; m < 3 ; m ++ ) {
			for( int q = 0 ; q < 4 ; q ++ ) {
				fixture.quarter(m, q, 1, 0);
			}
		}
		start("level " + rms, fixture, settings, new TolerancePolicy(5.0, 25.0));
		for( int i = 0 ; i < 4 ; i ++ ) {
			for( int k = 0 ; k < 14 ; k ++ ) {
				frame(hz(E4), 0.95, k == 0, rms, null, null);
			}
			hold(E4, rms, 400_000_000L);
			long end = this.now + 300_000_000L;
			while( this.now < end ) {
				frame(Double.NaN, 0.0, false, -75.0, null, null);
			}
		}
	}

	/** E4 struck after a silence with the expected pitch ahead of its neighbour by {@code score} - 0.1 (the early stability, when set). */
	private void evidence(RecognitionSettings settings, double score) {
		start("evidence " + score, twoNotes(), settings, new TolerancePolicy(5.0, 25.0));
		PracticeParameters parameters = PracticeParameters.of(settings);
		this.session.setEarlyStability(parameters.earlyStabilityMargin(), parameters.earlyStabilityNanos());
		long end = this.now + LATENCY_NANOS;
		while( this.now < end ) {
			frame(Double.NaN, 0.0, false, -20.0, null, null);
		}
		frame(hz(E4), 0.95, true, -20.0, null, null);
		end = this.now + 300_000_000L;
		while( this.now < end ) {
			TargetEvidence target = new TargetEvidence(Arrays.asList(new TargetEvidence.Hypothesis(E4, score), new TargetEvidence.Hypothesis(E4 + 1, 0.1)), 40.0);
			frame(hz(E4), 0.95, false, -20.0, null, target);
		}
	}

	/** E4 with a written vibrato, played with a vibrato for five seconds: its result comes at the end of the observation, from its attack. */
	private void vibrato(RecognitionSettings settings) {
		SongFixture fixture = new SongFixture(1);
		fixture.quarter(0, 0, 1, 0).getEffect().setVibrato(true);
		fixture.quarter(0, 1, 1, 1);
		start("vibrato", fixture, settings, new TolerancePolicy(5.0, 35.0));
		this.session.setTechniqueJudge(new TechniqueJudge(TechniqueCatalog.of(new VibratoDetector(VibratoDetector.Parameters.defaults()), true, "test"), true));
		long begin = this.now;
		for( int k = 0 ; k < 14 ; k ++ ) {
			frame(hz(E4 + 0.3 * Math.sin(2.0 * Math.PI * 5.5 * (this.now - begin) / 1e9)), 0.95, k == 13, -20.0, null, null);
		}
		long end = this.now + 5_000_000_000L;
		while( this.now < end ) {
			frame(hz(E4 + 0.3 * Math.sin(2.0 * Math.PI * 5.5 * (this.now - begin) / 1e9)), 0.95, false, -20.0, null, null);
		}
	}

	/** D4 bent a whole tone at {@code tempo} quarters a minute, then held: its result comes at the end of its observation window. */
	private void bendObserved(RecognitionSettings settings, int tempo) {
		SongFixture fixture = wholeToneBendThenE4();
		fixture.tempoChange(0, tempo);
		start("bend " + tempo, fixture, settings, new TolerancePolicy(5.0, 25.0));
		this.session.setTechniqueJudge(new TechniqueJudge(TechniqueCatalog.of(new BendDetector(BendDetector.Parameters.defaults()), true, "test"), true));
		pluck(D4, 150_000_000L);
		glide(D4, E4, 250_000_000L);
		hold(E4, -20.0, 4_500_000_000L);
	}

	/** D4 bent a whole tone that goes {@code past} semitones past its target: still on the path, or not. */
	private void bendPastTarget(RecognitionSettings settings, double past) {
		start("past " + past, wholeToneBendThenE4(), settings, new TolerancePolicy(5.0, 25.0));
		pluck(D4, 150_000_000L);
		glide(D4, E4 + past, 250_000_000L);
		hold(E4 + past, -20.0, 500_000_000L);
	}

	// ---------------------------------------------------------------- the session and its frames

	private void start(String name, SongFixture fixture, RecognitionSettings settings, TolerancePolicy tolerance) {
		PracticeTrackSelection selection = new PracticeTrackSelection(fixture.song,
				PracticeTrackCandidates.evaluate(fixture.song, fixture.track()).getPreselected(), PracticeTunerContext.getInstance(new TGContext()));
		selection.accept();
		this.session = new PracticeSession(selection, fixture.sequence(), new NoteInterpreter(0.8), tolerance, STABILITY_NANOS, LATENCY_NANOS,
				new ChordMatcher(ChordMatcher.Parameters.defaults()), null);
		this.session.setWaitParameters(PracticeParameters.of(settings).waitParameters());
		this.session.setAssessment(SUITABLE);
		this.session.onCommand(PracticeCommand.START);
		this.now = 0;
		this.frameCount = 0;
		this.shown = null;
		this.last = null;
		this.log.append('\n').append(name).append(':');
	}

	/** One hop; what the snapshot shows is written down each time it changes, with the hop it changed on. */
	private PracticeSnapshot frame(double hz, double confidence, boolean onset, double rms, AttackEvidence attack, TargetEvidence target) {
		this.now += HOP_NANOS;
		this.frameCount ++;
		AnalysisFrame.LevelState level = (rms < -60.0 ? AnalysisFrame.LevelState.TOO_LOW : AnalysisFrame.LevelState.SUITABLE);
		PracticeSnapshot snapshot = this.session.onFrame(new AnalysisFrame(this.now, rms, rms + 6.0, 0.0, level, hz, confidence, onset, null, target, null, attack));
		this.last = snapshot;
		StringBuilder s = new StringBuilder();
		s.append(snapshot.getCurrentIndex()).append('/').append(snapshot.getAcceptedCount()).append(' ').append(snapshot.getVerdict());
		s.append(snapshot.isSpeedLimit() ? " speed" : "").append(snapshot.isInputTooWeak() ? " weak" : "");
		for( TechniqueResult result : snapshot.getTechniqueResults() ) {
			s.append(' ').append(result.getTechnique()).append('=').append(result.getVerdict()).append(result.getMeasurements());
		}
		if( !s.toString().equals(this.shown) ) {
			this.shown = s.toString();
			this.log.append(' ').append(this.frameCount).append('>').append(this.shown).append(';');
		}
		return snapshot;
	}

	/** The pitch sounds, the onset comes 13 hops later (as the detector reports it), the pitch goes on until the note is accepted (2 s at most). */
	private long pluckUntilAccepted(double midi) {
		int accepted = (this.last == null ? 0 : this.last.getAcceptedCount());
		long end = this.now + LATENCY_NANOS;
		while( this.now < end ) {
			frame(hz(midi), 0.95, false, -20.0, null, null);
		}
		frame(hz(midi), 0.95, true, -20.0, null, null);
		end = this.now + 2_000_000_000L;
		while( this.now < end ) {
			if( frame(hz(midi), 0.95, false, -20.0, null, null).getAcceptedCount() > accepted ) {
				return this.now;
			}
		}
		return this.now;
	}

	private void pluck(double midi, long holdNanos) {
		long end = this.now + LATENCY_NANOS;
		while( this.now < end ) {
			frame(hz(midi), 0.95, false, -20.0, null, null);
		}
		frame(hz(midi), 0.95, true, -20.0, null, null);
		hold(midi, -20.0, holdNanos);
	}

	private void hold(double midi, double rms, long nanos) {
		long end = this.now + nanos;
		while( this.now < end ) {
			frame(hz(midi), 0.95, false, rms, null, null);
		}
	}

	private void glide(double from, double to, long nanos) {
		long steps = Math.max(1, nanos / HOP_NANOS);
		for( long i = 1 ; i <= steps ; i ++ ) {
			frame(hz(from + (to - from) * i / steps), 0.95, false, -20.0, null, null);
		}
	}

	private void silence(long nanos) {
		long end = this.now + nanos;
		while( this.now < end ) {
			frame(Double.NaN, 0.0, false, -20.0, null, null);
		}
	}

	private static SongFixture twoNotes() {
		SongFixture fixture = new SongFixture(1);
		fixture.quarter(0, 0, 1, 0);
		fixture.quarter(0, 1, 1, 1);
		return fixture;
	}

	/** D4 bent a whole tone (string 3, fret 7), then E4 (string 3, fret 9). */
	private static SongFixture wholeToneBendThenE4() {
		SongFixture fixture = new SongFixture(1);
		fixture.bend(0, 0, 3, 7, 2);
		fixture.quarter(0, 1, 3, 9);
		return fixture;
	}

	private static double hz(double midi) {
		return 440.0 * Math.pow(2.0, (midi - 69.0) / 12.0);
	}

	/** {@code pitch} with the amplitude that makes the total {@code total}, every other candidate a tenth of it. */
	private static double[] note(int pitch, double total) {
		double main = total / (1.0 + 0.1 * (PITCHES.length - 1));
		double[] amplitudes = new double[PITCHES.length];
		for( int i = 0 ; i < PITCHES.length ; i ++ ) {
			amplitudes[i] = (PITCHES[i] == pitch ? main : 0.1 * main);
		}
		return amplitudes;
	}

	private static final double BIN_HZ = 0.5;

	/** A growth spectrum with a parabolic peak at each partial of {@code midi}, in tune (the attack's pitch is read from it). */
	private static double[] growth(int midi) {
		double[] growth = new double[8000];
		double f0 = hz(midi);
		for( int h = 1 ; h <= 12 ; h ++ ) {
			double center = f0 * h / BIN_HZ;
			for( int k = (int) Math.floor(center) - 2 ; k <= (int) Math.ceil(center) + 2 ; k ++ ) {
				double d = (k - center) / 2.0;
				growth[k] = Math.max(growth[k], 1.0 - d * d);
			}
		}
		return growth;
	}

	private static double[] equal(double amplitude) {
		double[] amplitudes = new double[PITCHES.length];
		Arrays.fill(amplitudes, amplitude);
		return amplitudes;
	}
}
