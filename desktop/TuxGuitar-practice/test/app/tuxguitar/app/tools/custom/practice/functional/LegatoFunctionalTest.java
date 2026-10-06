package app.tuxguitar.app.tools.custom.practice.functional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.model.PlayingTechnique;
import app.tuxguitar.app.tools.custom.practice.model.PracticeSequence;
import app.tuxguitar.app.tools.custom.practice.model.PracticeTrackCandidates;
import app.tuxguitar.app.tools.custom.practice.model.PracticeTrackSelection;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.model.TechniqueResult;
import app.tuxguitar.app.tools.custom.practice.model.TechniqueVerdict;
import app.tuxguitar.app.tools.custom.practice.session.AttackCandidates;
import app.tuxguitar.app.tools.custom.practice.session.ChordMatcher;
import app.tuxguitar.app.tools.custom.practice.session.PolyphonicAcceptance;
import app.tuxguitar.app.tools.custom.practice.session.PracticeCommand;
import app.tuxguitar.app.tools.custom.practice.session.PracticeParameters;
import app.tuxguitar.app.tools.custom.practice.session.PracticeSession;
import app.tuxguitar.app.tools.custom.practice.session.PracticeSnapshot;
import app.tuxguitar.app.tools.custom.practice.session.TechniqueJudge;
import app.tuxguitar.audio.engine.OfflineAnalysis;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.audio.engine.session.PracticeTunerContext;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.audio.engine.technique.TechniqueCatalog;
import app.tuxguitar.audio.engine.testsupport.TechniqueSignals;
import app.tuxguitar.util.TGContext;

/**
 * Hammer-on and pull-off through the whole practice chain on synthetic signals (spec 002 T136, Phase 7): a score with a written legato pair
 * (SongFixture.legatoPair), the legato step without any attack (a pitch step of a continuing tone) and, beside it, the same two notes picked.
 * What is reported for each interval and direction: whether the destination note is accepted from its pitch, and what the technique line says.
 * Asserted: the picked version is accepted, and a technique is never reported as heard (no detector is verified yet: "cannot be checked yet"),
 * which is the honest answer until the legato detector has real recordings (research R4, the owner's decision of 2026-10-06 puts legato
 * without a new attack in the technique phase). The acceptance of the legato destination is reported and not asserted: it is the figure the
 * work on hammer-ons without attack starts from.
 */
public class LegatoFunctionalTest {

	private static final int RATE = 48000;
	private static final int SAMPLES = 2 * RATE + RATE / 2;
	private static final double STEP_SECOND = 1.0;

	private static final class Outcome {
		final boolean destinationAccepted;
		final double destinationDelay;
		final TechniqueVerdict verdict;

		Outcome(boolean accepted, double delay, TechniqueVerdict verdict) {
			this.destinationAccepted = accepted;
			this.destinationDelay = delay;
			this.verdict = verdict;
		}
	}

	/** Practice on a score with the legato pair from {@code fromMidi} to {@code toMidi} (string 2: B3 = 59 open), over the given audio. */
	private static Outcome run(int fromMidi, int toMidi, float[] audio) {
		SongFixture fixture = new SongFixture(1);
		fixture.legatoPair(0, 0, 2, fromMidi - 59, toMidi - 59);
		FunctionalRun run = FunctionalRun.of(fixture, audio);
		TechniqueVerdict verdict = null;
		for( TechniqueResult result : run.techniqueResults ) {
			if( result.getTechnique() == PlayingTechnique.HAMMER_ON || result.getTechnique() == PlayingTechnique.PULL_OFF ) {
				verdict = result.getVerdict();
			}
		}
		boolean accepted = run.accepted >= 2;
		return new Outcome(accepted, accepted ? run.acceptedAt.get(1) - STEP_SECOND : Double.NaN, verdict);
	}

	@Test
	public void legatoAndPickedPairsThroughThePracticeChain() {
		StringBuilder report = new StringBuilder("legato pairs, synthetic signals: destination note accepted (delay after the step) / the technique line, legato step beside the picked pair\n");
		report.append(String.format("%-22s %-34s %-34s%n", "pair", "legato step (no attack)", "picked"));
		List<String> failures = new ArrayList<String>();
		int[][] pairs = { { 64, 66 }, { 64, 67 }, { 64, 69 }, { 67, 64 }, { 69, 64 }, { 66, 64 } };
		for( int[] pair : pairs ) {
			Outcome legato = run(pair[0], pair[1], TechniqueSignals.legatoStep(pair[0], pair[1], RATE, STEP_SECOND, SAMPLES));
			Outcome picked = run(pair[0], pair[1], TechniqueSignals.pickedPair(pair[0], pair[1], RATE, (int) (STEP_SECOND * RATE), SAMPLES, 5L));
			report.append(String.format("%-22s %-34s %-34s%n", (pair[1] > pair[0] ? "hammer-on " : "pull-off ") + pair[0] + " to " + pair[1], describe(legato), describe(picked)));
			if( !picked.destinationAccepted ) {
				failures.add("picked pair " + pair[0] + " to " + pair[1] + ": the destination was not accepted");
			}
			for( Outcome outcome : new Outcome[] { legato, picked } ) {
				if( outcome.verdict == TechniqueVerdict.DETECTED ) {
					failures.add("pair " + pair[0] + " to " + pair[1] + ": a technique was reported as heard although no detector is verified");
				}
			}
		}
		System.out.println(report);
		assertEquals(new ArrayList<String>(), failures, report.toString());
		assertTrue(report.length() > 0);
	}

	/**
	 * A run of four legato notes (a hammer-on, a hammer-on and a pull-off on one string, no attack after the first) at a rising speed: how many of
	 * the notes after the first are accepted from their pitch alone. Reported, with the shortest interval at which each note is followed; it is the
	 * baseline for the hammer-on and pull-off work (the reference recording's run is spaced by 91 to 108 ms).
	 */
	@Test
	public void legatoRunsAtRisingSpeed() {
		StringBuilder report = new StringBuilder("legato runs of four notes on one string (frets 0, 2, 4, 2), notes accepted in all (the first included), by spacing:\n");
		int[] midi = { 59, 61, 63, 61 };
		List<String> failures = new ArrayList<String>();
		for( double spacing : new double[] { 0.09, 0.11, 0.15, 0.2, 0.3, 0.4 } ) {
			SongFixture fixture = new SongFixture(1);
			fixture.quarter(0, 0, 2, 0).getEffect().setHammer(true);
			fixture.quarter(0, 1, 2, 2).getEffect().setHammer(true);
			fixture.quarter(0, 2, 2, 4).getEffect().setHammer(true);
			fixture.quarter(0, 3, 2, 2);
			PracticeSequence sequence = fixture.sequence();
			PracticeTrackSelection selection = new PracticeTrackSelection(fixture.song, PracticeTrackCandidates.evaluate(fixture.song, fixture.track()).getPreselected(),
					PracticeTunerContext.getInstance(new TGContext()));
			selection.accept();
			RecognitionSettings settings = RecognitionSettings.defaults();
			PracticeParameters parameters = PracticeParameters.of(settings);
			PracticeSession session = new PracticeSession(selection, sequence, parameters.noteInterpreter(), parameters.tolerance(), parameters.stabilityNanos(),
					parameters.articulationLatencyNanos(), new ChordMatcher(parameters.chordParameters()), null);
			session.setPolyphonicAcceptance(new PolyphonicAcceptance(PolyphonicAcceptance.Parameters.defaults()));
			session.setTechniqueJudge(new TechniqueJudge(TechniqueCatalog.defaults(), true));
			session.setAssessment(new InputAssessment(InputAssessment.Status.SUITABLE, InputAssessment.Reason.NONE));
			session.onCommand(PracticeCommand.START);
			double first = 1.0;
			float[] tone = TechniqueSignals.tone(RATE, SAMPLES + RATE, t -> {
				int step = (int) Math.floor((t - first) / spacing);
				return 440.0 * Math.pow(2.0, (midi[Math.max(0, Math.min(3, step))] - 69) / 12.0);
			}, 0.8, 0.5, 0.6, 0.4, 0.3);
			float[] samples = new float[RATE / 2 + tone.length];
			System.arraycopy(tone, 0, samples, RATE / 2, tone.length);
			int accepted = 0;
			for( AnalysisFrame frame : OfflineAnalysis.analyze(samples, RATE, settings, false, AttackCandidates.of(sequence)) ) {
				PracticeSnapshot snapshot = session.onFrame(frame);
				accepted = Math.max(accepted, snapshot.getAcceptedCount());
				for( TechniqueResult result : snapshot.getTechniqueResults() ) {
					if( result.getVerdict() == TechniqueVerdict.DETECTED ) {
						failures.add("a technique was reported as heard at spacing " + spacing);
					}
				}
			}
			report.append(String.format(java.util.Locale.ROOT, "  %3.0f ms: %d of 4 notes accepted in all%n", 1000 * spacing, accepted));
		}
		System.out.println(report);
		assertEquals(new ArrayList<String>(), failures, report.toString());
	}

	private static String describe(Outcome o) {
		return (o.destinationAccepted ? String.format("accepted (%+.0f ms)", 1000 * o.destinationDelay) : "not accepted") + " / " + (o.verdict == null ? "no line yet" : o.verdict.name().toLowerCase().replace('_', ' '));
	}
}
