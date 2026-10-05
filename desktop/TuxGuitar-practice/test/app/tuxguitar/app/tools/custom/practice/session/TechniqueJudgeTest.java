package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.model.PlayingTechnique;
import app.tuxguitar.app.tools.custom.practice.model.TechniqueResult;
import app.tuxguitar.app.tools.custom.practice.model.TechniqueTarget;
import app.tuxguitar.app.tools.custom.practice.model.TechniqueVerdict;
import app.tuxguitar.audio.engine.technique.DetectableTechnique;
import app.tuxguitar.audio.engine.technique.NoteSegment;
import app.tuxguitar.audio.engine.technique.TechniqueCatalog;
import app.tuxguitar.audio.engine.technique.TechniqueDetector;
import app.tuxguitar.audio.engine.technique.TechniqueEvidence;

/** Spec 002 T056: honest verdicts, and "not verifiable" whenever nothing justifies a verdict. */
public class TechniqueJudgeTest {

	private static final NoteSegment SEGMENT = new NoteSegment(new double[] { 60, 60, 60 }, new double[] { -20, -20, -20 }, new boolean[3], 0.006);

	private static TechniqueDetector detector(DetectableTechnique technique, TechniqueEvidence.Verdict verdict) {
		return new TechniqueDetector() {
			@Override
			public DetectableTechnique technique() {
				return technique;
			}

			@Override
			public TechniqueEvidence evaluate(NoteSegment segment) {
				return new TechniqueEvidence(technique, verdict, 0.9, Collections.singletonMap("depthCents", 40.0));
			}
		};
	}

	private static List<TechniqueTarget> vibrato() {
		return Collections.singletonList(TechniqueTarget.of(PlayingTechnique.VIBRATO));
	}

	@Test
	public void aTechniqueWhoseBenchmarkIsNotMetIsNotVerifiable() {
		TechniqueCatalog catalog = TechniqueCatalog.of(detector(DetectableTechnique.VIBRATO, TechniqueEvidence.Verdict.DETECTED), false, "benchmark below the target");

		TechniqueResult result = new TechniqueJudge(catalog, true).judge(vibrato(), SEGMENT).get(0);

		assertEquals(TechniqueVerdict.NOT_VERIFIABLE, result.getVerdict());
	}

	@Test
	public void switchingDetectionOffMakesEverythingNotVerifiable() {
		TechniqueCatalog catalog = TechniqueCatalog.of(detector(DetectableTechnique.VIBRATO, TechniqueEvidence.Verdict.DETECTED), true, "meets the target");

		assertEquals(TechniqueVerdict.NOT_VERIFIABLE, new TechniqueJudge(catalog, false).judge(vibrato(), SEGMENT).get(0).getVerdict());
	}

	@Test
	public void aTechniqueWithoutDetectorIsNotVerifiable() {
		TechniqueCatalog catalog = TechniqueCatalog.of(detector(DetectableTechnique.VIBRATO, TechniqueEvidence.Verdict.DETECTED), true, "meets the target");
		List<TechniqueTarget> palmMute = Collections.singletonList(TechniqueTarget.of(PlayingTechnique.PALM_MUTE));

		assertEquals(TechniqueVerdict.NOT_VERIFIABLE, new TechniqueJudge(catalog, true).judge(palmMute, SEGMENT).get(0).getVerdict());
	}

	@Test
	public void aVerifiedDetectorsVerdictIsReportedWithItsMeasurements() {
		for( TechniqueEvidence.Verdict verdict : TechniqueEvidence.Verdict.values() ) {
			TechniqueCatalog catalog = TechniqueCatalog.of(detector(DetectableTechnique.VIBRATO, verdict), true, "meets the target");

			TechniqueResult result = new TechniqueJudge(catalog, true).judge(vibrato(), SEGMENT).get(0);

			assertEquals(TechniqueVerdict.valueOf(verdict.name()), result.getVerdict());
			assertTrue(result.getVerdict().isDetectorVerdict());
			assertEquals(40.0, result.getMeasurements().get("depthCents"), 1e-9);
		}
	}

	@Test
	public void aVerifiedTechniqueWithNoMeasurementIsNotVerifiable() {
		TechniqueCatalog catalog = TechniqueCatalog.of(detector(DetectableTechnique.VIBRATO, TechniqueEvidence.Verdict.DETECTED), true, "meets the target");

		assertEquals(TechniqueVerdict.NOT_VERIFIABLE, new TechniqueJudge(catalog, true).judge(vibrato(), null).get(0).getVerdict());
	}

	@Test
	public void displayOnlyTechniquesGetNoResult() {
		List<TechniqueTarget> accent = Collections.singletonList(TechniqueTarget.of(PlayingTechnique.ACCENT));

		assertTrue(new TechniqueJudge(TechniqueCatalog.defaults(), true).judge(accent, SEGMENT).isEmpty());
	}

	@Test
	public void noShippedDetectorIsVerifiedBeforeItsBenchmarkSaysSo() {
		TechniqueJudge judge = new TechniqueJudge(TechniqueCatalog.defaults(), true);
		for( PlayingTechnique technique : new PlayingTechnique[] { PlayingTechnique.HAMMER_ON, PlayingTechnique.PULL_OFF, PlayingTechnique.SLIDE,
				PlayingTechnique.BEND, PlayingTechnique.VIBRATO } ) {
			assertTrue(judge.isNeverVerdict(technique), technique.name());
		}
	}
}
