package app.tuxguitar.app.tools.custom.practice.functional;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.model.TechniqueResult;
import app.tuxguitar.app.tools.custom.practice.model.TechniqueVerdict;
import app.tuxguitar.audio.engine.testsupport.SyntheticSignals;
import app.tuxguitar.audio.engine.testsupport.TechniqueSignals;

/**
 * One functional run per technique family on synthetic signals (spec 002 T136): a score with the technique written, a clip of it and, beside it,
 * the plain counterpart. Reported: whether the written note is accepted from its pitch and what the technique line says. Asserted: the plain
 * counterpart is accepted, and no technique is ever reported as heard while no detector is verified (research R4): a plain note gets no
 * "detected" and a technique note gets "cannot be checked yet" (or nothing, for the display-only ones). The detectors' own measurements on
 * recordings are in the dataset benchmark; real clips of the techniques the datasets lack are still to be recorded (T053, deferred).
 */
public class TechniqueFamiliesFunctionalTest {

	private static final int RATE = FunctionalRun.RATE;
	private static final int SAMPLES = 2 * RATE;

	private interface Score {
		void write(SongFixture fixture);
	}

	private static String describe(FunctionalRun run) {
		StringBuilder verdicts = new StringBuilder();
		for( TechniqueResult result : run.techniqueResults ) {
			verdicts.append(verdicts.length() > 0 ? ", " : "").append(result.getTechnique().name().toLowerCase()).append(" ").append(result.getVerdict().name().toLowerCase().replace('_', ' '));
		}
		return run.accepted + " of " + run.eventCount + " accepted" + (run.autoPassed > 0 ? ", " + run.autoPassed + " passed automatically (not judged)" : "") + (verdicts.length() > 0 ? "; " + verdicts : "");
	}

	@Test
	public void everyTechniqueFamilyIsAnsweredHonestlyAndTheNoteStillCounts() {
		StringBuilder report = new StringBuilder(String.format("%-18s %-52s %s%n", "family", "with the technique", "plain counterpart"));
		List<String> failures = new ArrayList<String>();

		class Case {
			final String name;
			final Score score;
			final Score plainScore;
			final float[] technique;
			final float[] plain;

			Case(String name, Score score, Score plainScore, float[] technique, float[] plain) {
				this.name = name;
				this.score = score;
				this.plainScore = plainScore;
				this.technique = technique;
				this.plain = plain;
			}
		}
		// E4 (string 1, open) with the effect, and the same note plain
		Score plainNote = f -> f.quarter(0, 0, 1, 0);
		float[] plucked = SyntheticSignals.karplusStrong(SyntheticSignals.midiFrequency(64), RATE, SAMPLES, 3L);
		List<Case> cases = new ArrayList<Case>();
		cases.add(new Case("vibrato", f -> f.quarter(0, 0, 1, 0).getEffect().setVibrato(true), plainNote,
				TechniqueSignals.vibrato(64, RATE, SAMPLES, 6.0, 40.0, 0.6, 0.4, 0.3), plucked));
		cases.add(new Case("palm mute", f -> f.quarter(0, 0, 1, 0).getEffect().setPalmMute(true), plainNote,
				TechniqueSignals.palmMutedPluck(64, RATE, SAMPLES, 3L), plucked));
		cases.add(new Case("slide", f -> {
			f.quarter(0, 0, 2, 5).getEffect().setSlide(true);
			f.quarter(0, 1, 2, 7);
		}, f -> {
			f.quarter(0, 0, 2, 5);
			f.quarter(0, 1, 2, 7);
		}, TechniqueSignals.slide(64, 66, RATE, 1.0, 0.25, SAMPLES + RATE), TechniqueSignals.pickedPair(64, 66, RATE, RATE, SAMPLES + RATE, 5L)));
		cases.add(new Case("tremolo picking", f -> {
			app.tuxguitar.song.models.effects.TGEffectTremoloPicking tremolo = f.factory().newEffectTremoloPicking();
			tremolo.getDuration().setValue(app.tuxguitar.song.models.TGDuration.SIXTEENTH);
			f.quarter(0, 0, 1, 0).getEffect().setTremoloPicking(tremolo);
		}, plainNote, TechniqueSignals.tremoloPicking(64, RATE, SAMPLES, 12.0, 1.5, 3L), plucked));
		cases.add(new Case("bend", f -> f.bend(0, 0, 1, 0, 1), plainNote, TechniqueSignals.bend(64, RATE, SAMPLES, 0.0, 0.0, 1.0, 1.0), plucked));
		cases.add(new Case("natural harmonic", f -> f.naturalHarmonic(0, 0, 3, 12), plainNote, TechniqueSignals.naturalHarmonic(67, RATE, SAMPLES), plucked));
		for( Case c : cases ) {
			SongFixture with = new SongFixture(1);
			c.score.write(with);
			SongFixture without = new SongFixture(1);
			c.plainScore.write(without);
			FunctionalRun technique = FunctionalRun.of(with, c.technique);
			FunctionalRun plain = FunctionalRun.of(without, c.plain);
			report.append(String.format("%-18s %-52s %s%n", c.name, describe(technique), describe(plain)));
			for( FunctionalRun run : new FunctionalRun[] { technique, plain } ) {
				for( TechniqueResult result : run.techniqueResults ) {
					if( result.getVerdict() == TechniqueVerdict.DETECTED ) {
						failures.add(c.name + ": a technique was reported as heard although no detector is verified");
					}
				}
			}
			if( plain.accepted < plain.eventCount ) {
				failures.add(c.name + ": the plain counterpart was not accepted in full (" + plain.accepted + " of " + plain.eventCount + ")");
			}
		}
		System.out.println(report);
		assertEquals(new ArrayList<String>(), failures, report.toString());
	}
}
