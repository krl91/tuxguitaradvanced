package app.tuxguitar.app.tools.custom.practice.session;

import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.MS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.model.PlayingTechnique;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.model.TechniqueVerdict;
import app.tuxguitar.audio.engine.technique.BendDetector;
import app.tuxguitar.audio.engine.technique.TechniqueCatalog;
import app.tuxguitar.song.models.TGBeat;
import app.tuxguitar.song.models.TGMeasure;
import app.tuxguitar.song.models.TGNote;
import app.tuxguitar.song.models.TGTrack;
import app.tuxguitar.song.models.TGVoice;

/**
 * The summary of a run (spec 002 T099, FR-056): every kind of verdict counted apart, the techniques never added to the timing, the events that were
 * not judged on their own, a total that is always the events of the run, and nothing written anywhere.
 */
public class FollowSummaryTest {

	private static SongFixture fiveNotes() {
		SongFixture fixture = new SongFixture(2);
		for (int beat = 0; beat < 4; beat++) {
			fixture.quarter(0, beat, 1, beat);
		}
		fixture.quarter(1, 0, 1, 4);
		return fixture;
	}

	private static void assertTotal(FollowSummary summary) {
		assertEquals(summary.events(), summary.onTime() + summary.early() + summary.late() + summary.wrong() + summary.missed() + summary.notJudged()
				+ summary.autoPassed() + summary.pending(), "every event is counted once");
	}

	@Test
	public void everyKindOfVerdictIsCountedApartAndTheTotalIsTheEventsOfTheRun() {
		FakePlayerRun run = new FakePlayerRun(fiveNotes(), 100);
		run.start();
		run.playEvent(0, 0);                                // on time
		run.playEvent(1, -150);                             // early
		run.play(run.target(2), 70, 600 * MS);              // wrong
		// event 3 is not played: missed
		run.playEvent(4, 150);                              // late
		run.play(run.target(0) + 250 * MS, 71, 120 * MS);   // an extra note
		run.runAll();

		FollowSummary summary = FollowSummary.of(run.last);

		assertEquals(5, summary.events());
		assertEquals(1, summary.onTime());
		assertEquals(1, summary.early());
		assertEquals(1, summary.late());
		assertEquals(1, summary.wrong());
		assertEquals(1, summary.missed());
		assertEquals(0, summary.notJudged());
		assertEquals(1, summary.extra());
		assertEquals(3, summary.rightNotes());
		assertEquals(5, summary.judged());
		assertEquals(0, summary.pending());
		assertTotal(summary);
	}

	@Test
	public void theEventsOfAPauseAreNotJudgedAndAreNeitherRightNorWrongInTheSummary() {
		FakePlayerRun run = new FakePlayerRun(fiveNotes(), 100);
		run.start();
		for (int i = 0; i < 5; i++) {
			run.playEvent(i, 0);
		}
		run.runUntil(run.target(1) + 250 * MS);
		run.session.pause(PracticeSession.PauseReason.INPUT_UNUSABLE);
		run.runUntil(run.target(3) + 250 * MS);
		run.session.resume(PracticeSession.PauseReason.INPUT_UNUSABLE);
		run.runAll();

		FollowSummary summary = FollowSummary.of(run.last);

		assertEquals(2, summary.notJudged(), "events 2 and 3 went by while paused");
		assertEquals(0, summary.missed());
		assertEquals(0, summary.wrong());
		assertEquals(3, summary.onTime());
		assertEquals(3, summary.judged());
		assertTotal(summary);
	}

	@Test
	public void aRunStoppedEarlyCountsTheEventsItNeverReachedAsPending() {
		FakePlayerRun run = new FakePlayerRun(fiveNotes(), 100);
		run.start();
		run.playEvent(0, 0);
		run.playEvent(1, 0);
		run.runUntil(run.target(1) + 300 * MS);
		run.session.stop();
		run.runUntil(run.now + 100 * MS);

		FollowSummary summary = FollowSummary.of(run.session.snapshot());

		assertEquals(2, summary.onTime());
		assertEquals(3, summary.pending());
		assertEquals(0, summary.missed(), "what was never reached is not a miss");
		assertTotal(summary);
	}

	@Test
	public void theTechniquesAreCountedApartAndNeverChangeTheTimingCounts() {
		SongFixture fixture = new SongFixture(1);
		fixture.bend(0, 0, 3, 7, 2);
		fixture.quarter(0, 1, 1, 1);
		TechniqueJudge judge = new TechniqueJudge(TechniqueCatalog.of(new BendDetector(BendDetector.Parameters.defaults()), true, "test"), true);
		FollowSummary[] summaries = new FollowSummary[2];
		for (int made = 0; made < 2; made++) {
			FakePlayerRun run = new FakePlayerRun(fixture, 100);
			run.session.setTechniqueJudge(judge);
			run.start();
			final boolean bent = (made == 1);
			run.playMoving(run.target(0), 450 * MS, seconds -> 62.0 + (bent ? 2.0 * Math.min(1.0, Math.max(0.0, (seconds - 0.1) / 0.2)) : 0.0));
			run.playEvent(1, 0);
			run.runAll();
			summaries[made] = FollowSummary.of(run.last);
		}

		assertEquals(summaries[0].onTime(), summaries[1].onTime());
		assertEquals(2, summaries[1].onTime());
		assertEquals(summaries[0].judged(), summaries[1].judged());
		assertEquals(1, summaries[1].techniqueCount(TechniqueVerdict.DETECTED), "the bend made");
		assertEquals(0, summaries[0].techniqueCount(TechniqueVerdict.DETECTED), "the bend not made");
		assertEquals(1, summaries[0].techniqueTotal());
		assertEquals(1, summaries[1].techniques().get(PlayingTechnique.BEND).values().stream().mapToInt(Integer::intValue).sum());
		assertTrue(summaries[0].techniqueVerdicts().containsKey(TechniqueVerdict.UNCERTAIN), "every verdict is there, 0 when none");
	}

	@Test
	public void theSummaryIsTheSameForTheSamePerformanceAndCannotBeChanged() {
		FollowSummary[] summaries = new FollowSummary[2];
		for (int i = 0; i < 2; i++) {
			FakePlayerRun run = new FakePlayerRun(fiveNotes(), 70);
			run.start();
			run.playEvent(0, 0);
			run.playEvent(1, -150);
			run.runAll();
			summaries[i] = FollowSummary.of(run.last);
		}

		assertEquals(summaries[0].onTime(), summaries[1].onTime());
		assertEquals(summaries[0].early(), summaries[1].early());
		assertEquals(summaries[0].missed(), summaries[1].missed());
		assertEquals(summaries[0].techniqueVerdicts(), summaries[1].techniqueVerdicts());
		assertThrows(UnsupportedOperationException.class, () -> summaries[0].techniqueVerdicts().put(TechniqueVerdict.DETECTED, 9));
		assertThrows(UnsupportedOperationException.class, () -> summaries[0].techniques().clear());
	}

	/** A digest of everything the song says about its notes and tracks. */
	private static String digest(SongFixture fixture) {
		StringBuilder digest = new StringBuilder();
		for (int t = 0; t < fixture.song.countTracks(); t++) {
			TGTrack track = fixture.song.getTrack(t);
			digest.append("track ").append(track.getNumber()).append(" mute=").append(track.isMute()).append(" solo=").append(track.isSolo()).append('\n');
			for (int m = 0; m < track.countMeasures(); m++) {
				TGMeasure measure = track.getMeasure(m);
				for (int b = 0; b < measure.countBeats(); b++) {
					TGBeat beat = measure.getBeat(b);
					for (int v = 0; v < beat.countVoices(); v++) {
						TGVoice voice = beat.getVoice(v);
						for (int n = 0; n < voice.countNotes(); n++) {
							TGNote note = voice.getNote(n);
							digest.append(m).append(':').append(beat.getStart()).append(':').append(note.getString()).append(':').append(note.getValue()).append('\n');
						}
					}
				}
			}
		}
		return digest.toString();
	}

	@Test
	public void aWholeRunAndItsSummaryChangeNothingInTheSong() {
		SongFixture fixture = fiveNotes();
		String before = digest(fixture);
		boolean muteBefore = fixture.track().isMute();
		boolean soloBefore = fixture.track().isSolo();
		FakePlayerRun run = new FakePlayerRun(fixture, 100);
		run.start();
		for (int i = 0; i < 5; i++) {
			run.playEvent(i, 0);
		}
		run.runAll();
		FollowSummary.of(run.last);
		run.playback.stop();

		assertEquals(before, digest(fixture));
		assertEquals(muteBefore, fixture.track().isMute(), "the mute of the song is the song's: the run muted the player only");
		assertEquals(soloBefore, fixture.track().isSolo());
	}
}
