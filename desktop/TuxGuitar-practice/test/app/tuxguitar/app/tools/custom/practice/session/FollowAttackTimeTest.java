package app.tuxguitar.app.tools.custom.practice.session;

import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.HOP;
import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.LATENCY;
import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.MS;
import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.T0;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.session.FollowSession.Status;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.analysis.AttackEvidence;

/**
 * The time of an attack (spec 002 T101, SC-004): it is the transient the detector reported, never the moment the attack evidence became strong enough.
 * Over an identical note that still rings the evidence of an attack begins to grow late; the event used to take that late time (LATE, 90 ms off) and
 * an attack could be taken for the next event. Scripted frames with no pitch (f0 absent), so that only the attack evidence can match, and the evidence
 * and the report of the attack placed where the benchmark showed them.
 */
public class FollowAttackTimeTest {

	/** Four identical quarter notes at 240 a minute: one every 250 ms. */
	private static FakePlayerRun run(boolean waits) {
		SongFixture fixture = new SongFixture(1);
		for (int beat = 0; beat < 4; beat++) {
			fixture.quarter(0, beat, 1, 0);
		}
		FakePlayerRun run = new FakePlayerRun(fixture, 200, LATENCY, 3_000 * MS);
		run.session.setPolyphonicAcceptance(() -> new PolyphonicAcceptance(PolyphonicAcceptance.Parameters.defaults()));
		run.session.setWaitsForReportedAttack(waits);
		run.start();
		return run;
	}

	/** The attack evidence of one pitch: its harmonics grown at their exact places (so that its tuning is measurable), 10 Hz bins. */
	private static AttackEvidence evidence(int midi, double amplitude) {
		double f0 = 440.0 * Math.pow(2.0, (midi - 69) / 12.0);
		double[] growth = new double[2000];
		for (int h = 1; h <= 8; h++) {
			int bin = (int) Math.round(f0 * h / 10.0);
			if (bin > 0 && bin < growth.length - 1) {
				growth[bin] = amplitude / h;
				growth[bin - 1] = amplitude / h / 3;
				growth[bin + 1] = amplitude / h / 3;
			}
		}
		return new AttackEvidence(new int[] { midi }, new double[] { amplitude }, 0.3, growth, 10.0);
	}

	/**
	 * Runs the frames. The detector reports the attack at each of {@code attacks} LATENCY later; the evidence of attack k begins
	 * {@code evidenceDelaysMillis[k]} after it and lasts 150 ms.
	 */
	private static void play(FakePlayerRun run, int midi, long[] attacks, long[] evidenceDelaysMillis) {
		long end = T0 + run.lead + run.timeline.durationNanos() + 1_500 * MS;
		for (long wall = T0; wall < end; wall += HOP) {
			run.advanceTo(wall);
			boolean flag = false;
			AttackEvidence evidence = null;
			for (int k = 0; k < attacks.length; k++) {
				flag |= (wall == attacks[k] + LATENCY);
				long begins = attacks[k] + evidenceDelaysMillis[k] * MS;
				if (wall >= begins && wall < begins + 150 * MS) {
					evidence = evidence(midi, 1.0);
				}
			}
			AnalysisFrame frame = new AnalysisFrame(wall - T0, -20.0, -10.0, 0.0, AnalysisFrame.LevelState.SUITABLE, Double.NaN, 0.0, flag, null, null, null,
					evidence != null ? evidence : new AttackEvidence(new int[] { midi }, new double[] { 0.0 }, 0.0));
			run.last = run.session.onFrame(frame);
		}
	}

	@Test
	public void anEventWhoseNoteRingsTakesTheTimeOfTheTransientTheDetectorReportedNotTheLateTimeOfTheEvidence() {
		for (boolean waits : new boolean[] { false, true }) {
			FakePlayerRun run = run(waits);
			// event 0 rings under event 1 (the same pitch); the evidence of the attack of event 1 begins 90 ms after it
			long first = run.target(0);
			long second = run.target(1) + 10 * MS;
	
			play(run, 64, new long[] { first, second }, new long[] { 20, 90 });
	
			FollowSession.EventResult result = run.last.results().get(1);
			assertEquals(Status.MATCHED, result.status());
			assertEquals(10 * MS, result.offsetNanos(), 2 * MS, "the offset is that of the transient: " + result.offsetNanos() / MS + " ms");
			assertEquals(TimingVerdict.ON_TIME, result.timing(), "the late evidence did not make it late");
		}
	}

	@Test
	public void theEvidenceOfAnAttackThatBelongsToTheEventBeforeIsNotMovedToTheNextEvent() {
		for (boolean waits : new boolean[] { false, true }) {
			FakePlayerRun run = run(waits);
			// events 0 and 1 are played; the evidence of the attack of event 1 begins 130 ms after it, inside the window of event 2 (250 ms further)
			play(run, 64, new long[] { run.target(0), run.target(1) }, new long[] { 20, 130 });
	
			assertEquals(Status.MATCHED, run.last.results().get(1).status());
			assertEquals(TimingVerdict.ON_TIME, run.last.results().get(1).timing());
			assertEquals(Status.MISSED, run.last.results().get(2).status(), "the attack of event 1 does not become the attack of event 2");
			assertEquals(0, run.last.counts().extra(), "and it is not an extra note either");
		}
	}

	@Test
	public void eachOfTwoIdenticalNotesTakesItsOwnAttackAtItsOwnTime() {
		for (boolean waits : new boolean[] { false, true }) {
			FakePlayerRun run = run(waits);
	
			play(run, 64, new long[] { run.target(0) + 5 * MS, run.target(1) - 10 * MS, run.target(2) + 15 * MS }, new long[] { 20, 90, 90 });
	
			assertEquals(Status.MATCHED, run.last.results().get(1).status());
			assertEquals(Status.MATCHED, run.last.results().get(2).status());
			assertEquals(-10 * MS, run.last.results().get(1).offsetNanos(), 2 * MS);
			assertEquals(15 * MS, run.last.results().get(2).offsetNanos(), 2 * MS);
			assertEquals(3, run.last.counts().onTime());
			assertEquals(0, run.last.counts().extra());
		}
	}

	@Test
	public void anAttackNobodyReportedIsStillMatchedFromItsEvidenceOnceTheDetectorHadTheTimeToReportIt() {
		for (boolean waits : new boolean[] { false, true }) {
			FakePlayerRun again = run(waits);
			long attack = again.target(0);
			long end = T0 + again.lead + again.timeline.durationNanos() + 1_500 * MS;
			for (long wall = T0; wall < end; wall += HOP) {
				again.advanceTo(wall);
				boolean evidenceNow = (wall >= attack + 20 * MS && wall < attack + 170 * MS);
				again.last = again.session.onFrame(new AnalysisFrame(wall - T0, -20.0, -10.0, 0.0, AnalysisFrame.LevelState.SUITABLE, Double.NaN, 0.0, false, null, null,
						null, evidenceNow ? evidence(64, 1.0) : new AttackEvidence(new int[] { 64 }, new double[] { 0.0 }, 0.0)));
			}
			assertEquals(Status.MATCHED, again.last.results().get(0).status(), "the evidence alone still matches when no attack was reported");
			assertTrue(Math.abs(again.last.results().get(0).offsetNanos()) <= 60 * MS);
		}
	}
}
