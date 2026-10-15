package app.tuxguitar.app.tools.custom.practice.calibration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.calibration.RecognitionRate;
import app.tuxguitar.audio.engine.settings.ProfileParameters;
import app.tuxguitar.audio.engine.settings.RecognitionParameter;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;

/**
 * Spec 013 (T022): the search is a deterministic coordinate descent that starts from the default and never ends worse than it (contract
 * scoring-search, FR-006, FR-014). Scored here by synthetic functions of the settings, not by audio.
 */
public class ProfileSearchTest {

	private static final RecognitionSettings DEFAULTS = RecognitionSettings.defaults();

	private static CalibrationScore score(int correct, int wrong) {
		return new CalibrationScore(correct, wrong, new RecognitionRate(Math.min(correct, 10), 10, Math.max(0, correct - 10), 3));
	}

	/** correct = 13 minus how many steps the accept value is from a target */
	private static ProfileSearch.Scoring towardsAccept(double target) {
		return settings -> {
			double step = (RecognitionParameters.NOTE_ACCEPT.getMaximum() - RecognitionParameters.NOTE_ACCEPT.getMinimum()) / 16.0;
			double distance = Math.abs(settings.get(RecognitionParameters.NOTE_ACCEPT) - target) / step;
			return score(Math.max(0, (int) Math.round(13 - distance)), 0);
		};
	}

	private static double farFromDefault() {
		RecognitionParameter.Number p = RecognitionParameters.NOTE_ACCEPT;
		return p.getDefault() - p.getMinimum() > p.getMaximum() - p.getDefault() ? p.getMinimum() : p.getMaximum();
	}

	@Test
	public void theDefaultIsTheFirstCandidateScored() {
		List<RecognitionSettings> asked = new ArrayList<RecognitionSettings>();
		new ProfileSearch(settings -> {
			asked.add(settings);
			return score(5, 0);
		}).run(DEFAULTS, null);
		assertEquals(DEFAULTS, asked.get(0));
	}

	@Test
	public void aSearchThatFindsNothingBetterKeepsTheDefault() {
		ProfileSearch.Result result = new ProfileSearch(settings -> score(7, 1)).run(DEFAULTS, null);
		assertEquals(DEFAULTS, result.getBest());
		assertFalse(result.isBetterThanDefault());
		assertEquals(result.getDefaultScore().getObjective(), result.getBestScore().getObjective());
		for (ProfileSearch.Line line : result.getReport()) {
			assertFalse(line.isChanged(), line.getKey() + " is left at the default");
		}
	}

	@Test
	public void aSettingThatHelpsIsFoundAndTheResultIsBetterThanTheDefault() {
		double target = farFromDefault();
		ProfileSearch.Result result = new ProfileSearch(towardsAccept(target)).run(DEFAULTS, null);
		assertTrue(result.isBetterThanDefault());
		assertTrue(result.getBestScore().getObjective() > result.getDefaultScore().getObjective());
		assertTrue(Math.abs(result.getBest().get(RecognitionParameters.NOTE_ACCEPT) - target) < Math.abs(DEFAULTS.get(RecognitionParameters.NOTE_ACCEPT) - target),
				"the acceptance moved towards the target");
		int changed = 0;
		for (ProfileSearch.Line line : result.getReport()) {
			if (line.isChanged()) {
				changed++;
			}
		}
		assertEquals(1, changed, "only the setting that helps changed");
	}

	@Test
	public void leavingTheDefaultNeedsAGainOfAtLeastOnePoint() {
		AtomicInteger calls = new AtomicInteger();
		// the default scores 5; the second candidate scores 5.5 (no whole point more): the default stays
		ProfileSearch.Result half = new ProfileSearch(settings -> calls.getAndIncrement() == 1 ? new HalfPoint() : score(5, 0)).run(DEFAULTS, null);
		assertFalse(half.isBetterThanDefault());
		assertEquals(DEFAULTS, half.getBest());
		AtomicInteger second = new AtomicInteger();
		ProfileSearch.Result whole = new ProfileSearch(settings -> second.getAndIncrement() == 1 ? score(6, 0) : score(5, 0)).run(DEFAULTS, null);
		assertTrue(whole.isBetterThanDefault());
		assertNotEquals(DEFAULTS, whole.getBest());
	}

	/** a score half a point above 5 */
	private static final class HalfPoint extends CalibrationScore {
		HalfPoint() {
			super(5, 0, new RecognitionRate(5, 10, 0, 3));
		}

		@Override
		public double getObjective() {
			return 5.5;
		}
	}

	@Test
	public void whenTwoCandidatesScoreTheSameTheOneNearestTheDefaultIsKept() {
		ProfileSearch.Result result = new ProfileSearch(settings -> {
			// every value of the acceptance scores the same, except far from the default
			return score(8, 0);
		}).run(DEFAULTS, null);
		assertEquals(DEFAULTS.get(RecognitionParameters.NOTE_ACCEPT), result.getBest().get(RecognitionParameters.NOTE_ACCEPT));
	}

	@Test
	public void theSameInputGivesTheSameResultAndTheSameOrderOfCandidates() {
		List<RecognitionSettings> first = new ArrayList<RecognitionSettings>();
		List<RecognitionSettings> second = new ArrayList<RecognitionSettings>();
		ProfileSearch.Result a = new ProfileSearch(settings -> {
			first.add(settings);
			return towardsAccept(farFromDefault()).score(settings);
		}).run(DEFAULTS, null);
		ProfileSearch.Result b = new ProfileSearch(settings -> {
			second.add(settings);
			return towardsAccept(farFromDefault()).score(settings);
		}).run(DEFAULTS, null);
		assertEquals(first, second);
		assertEquals(a.getBest(), b.getBest());
		assertEquals(a.getCandidates(), b.getCandidates());
	}

	@Test
	public void theBudgetInCandidatesStopsTheSearchWithTheBestFoundSoFar() {
		ProfileSearch search = new ProfileSearch(towardsAccept(farFromDefault()), 7, ProfileSearch.BUDGET_MILLIS, System::nanoTime);
		ProfileSearch.Result result = search.run(DEFAULTS, null);
		assertTrue(result.isStoppedAtLimit());
		assertEquals(7, result.getCandidates());
		assertFalse(result.isCancelled());
	}

	@Test
	public void theBudgetInTimeStopsTheSearch() {
		AtomicLong clock = new AtomicLong();
		ProfileSearch search = new ProfileSearch(settings -> {
			clock.addAndGet(30_000_000_000L);
			return score(5, 0);
		}, ProfileSearch.BUDGET_CANDIDATES, 120_000L, clock::get);
		ProfileSearch.Result result = search.run(DEFAULTS, null);
		assertTrue(result.isStoppedAtLimit(), "120 s used");
		assertTrue(result.getCandidates() <= 5, "stopped about when the time ran out: " + result.getCandidates());
	}

	@Test
	public void aCancelEndsWithinOneCandidate() {
		AtomicInteger calls = new AtomicInteger();
		ProfileSearch[] holder = new ProfileSearch[1];
		holder[0] = new ProfileSearch(settings -> {
			if (calls.incrementAndGet() == 3) {
				holder[0].cancel();
			}
			return score(5, 0);
		});
		ProfileSearch.Result result = holder[0].run(DEFAULTS, null);
		assertTrue(result.isCancelled());
		assertEquals(3, calls.get(), "no candidate is scored after the cancel");
	}

	@Test
	public void theReportHasOneLinePerProfileParameterInTheFixedOrder() {
		ProfileSearch.Result result = new ProfileSearch(towardsAccept(farFromDefault())).run(DEFAULTS, null);
		assertEquals(ProfileParameters.LIST.size(), result.getReport().size());
		for (int i = 0; i < ProfileParameters.LIST.size(); i++) {
			ProfileSearch.Line line = result.getReport().get(i);
			assertEquals(ProfileParameters.LIST.get(i).getKey(), line.getKey());
			assertEquals(DEFAULTS.get(ProfileParameters.LIST.get(i)), line.getDefaultValue());
			assertEquals(result.getBest().get(ProfileParameters.LIST.get(i)), line.getFoundValue());
		}
	}

	@Test
	public void theProgressIsReportedAfterEveryCandidate() {
		List<Integer> seen = new ArrayList<Integer>();
		new ProfileSearch(settings -> score(5, 0), 12, ProfileSearch.BUDGET_MILLIS, System::nanoTime).run(DEFAULTS, (done, budget) -> seen.add(done));
		assertEquals(12, seen.size());
		for (int i = 0; i < seen.size(); i++) {
			assertEquals(i + 1, seen.get(i).intValue());
		}
	}

	@Test
	public void everyCandidateStaysWithinTheBoundsOfItsParameter() {
		new ProfileSearch(settings -> {
			for (RecognitionParameter.Number parameter : ProfileParameters.LIST) {
				assertTrue(ProfileParameters.isValid(parameter, settings.get(parameter)), parameter.getKey());
			}
			return score(5, 0);
		}).run(DEFAULTS, null);
	}
}
