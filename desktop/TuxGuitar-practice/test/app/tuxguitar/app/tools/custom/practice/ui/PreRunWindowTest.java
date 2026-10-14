package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.session.FollowSettings;
import app.tuxguitar.app.tools.custom.practice.ui.PreRunCheck.State;
import app.tuxguitar.audio.engine.analysis.StrumClipMeter;
import app.tuxguitar.audio.engine.analysis.StrumVerdict;

/**
 * The small start window on a headless toolkit (spec 010 T015, T021): the live level, the sentence of the verdict and the advice, the buttons, and the
 * way out by Cancel, the close button or Escape. The native window is checked by {@code PracticeWindowSwtTest}.
 */
public class PreRunWindowTest {

	private static final long SECOND = 1_000_000_000L;

	/** A source whose verdict, level and boost the test sets; counts the resets. */
	private static final class FakeSource implements PreRunSource {
		StrumClipMeter.Reading reading = readingOf(StrumVerdict.NO_STRUM, 0.0, Double.NaN);
		double level = Double.NaN;
		boolean boost;
		final AtomicInteger resets = new AtomicInteger();
		int closed;

		@Override
		public StrumClipMeter.Reading reading() {
			return this.reading;
		}

		@Override
		public double levelDbfs() {
			return this.level;
		}

		@Override
		public boolean boostActive() {
			return this.boost;
		}

		@Override
		public void reset() {
			this.reading = readingOf(StrumVerdict.NO_STRUM, 0.0, Double.NaN);
			this.resets.incrementAndGet();
		}

		@Override
		public void close() {
			this.closed++;
		}
	}

	private static StrumClipMeter.Reading readingOf(StrumVerdict verdict, double share, double rms) {
		return new StrumClipMeter.Reading(verdict, share, rms);
	}

	private final SwtContractToolkit toolkit = new SwtContractToolkit();
	private FakeSource source = new FakeSource();
	private final AtomicInteger finished = new AtomicInteger();
	private long now = 5 * SECOND;
	private PreRunCheck check;
	private PreRunWindow window;

	private PreRunWindow open(State state) {
		this.source = new FakeSource(); // each window listens to a fresh source: a verdict of an earlier window does not carry over
		this.check = new PreRunCheck(FollowSettings.Mode.WAIT);
		if (state == State.STRUM) {
			this.check.answerTuned(true);
		}
		this.window = new PreRunWindow(this.toolkit.factory(), null, this.check, this.source, (key, arguments) -> key + (arguments.length == 0 ? "" : java.util.Arrays.toString(arguments)),
				() -> this.now, this.finished::incrementAndGet);
		this.window.refresh();
		return this.window;
	}

	private static boolean visible(Object widget) {
		return SwtContractToolkit.state(widget).properties.get("Visible") == Boolean.TRUE;
	}

	private static String text(Object widget) {
		Object text = SwtContractToolkit.state(widget).properties.get("Text");
		return text == null ? "" : text.toString();
	}

	@Test
	public void waitingForAStrumShowsTheLiveLevelAndNeverSaysTheLevelIsGood() {
		open(State.STRUM);
		this.source.level = -30.0;
		this.window.refresh();
		assertEquals(30, SwtContractToolkit.state(this.window.meterBar()).properties.get("Value"), "the bar follows the live level on the scale of the level panel (-60 dBFS is empty)");
		assertTrue(text(this.window.sentenceLabel()).contains("practice.prerun.verdict.NO_STRUM"), text(this.window.sentenceLabel()));
		assertFalse(text(this.window.sentenceLabel()).contains("GOOD"), "nothing is shown as good before a strum");
		assertFalse(visible(this.window.continueButton()), "no way to go on by itself before ten seconds");
		assertEquals(State.STRUM, this.check.state());
		assertEquals(0, this.finished.get());
	}

	@Test
	public void aBadVerdictBlocksAndTellsWhatToDoWithTheExistingAdviceAndTheBoostWarningWhenTheApplicationBoostsTheSignal() {
		open(State.STRUM);
		String[][] cases = { { "CLIPPING", "practice.level.guidance.CLIPPING" }, { "TOO_LOUD", "practice.level.guidance.TOO_HIGH" }, { "TOO_WEAK", "practice.level.guidance.TOO_LOW" } };
		for (String[] c : cases) {
			open(State.STRUM);
			this.source.reading = readingOf(StrumVerdict.valueOf(c[0]), 0.02, -12.0);
			this.window.refresh();
			assertEquals(State.LEVEL_BAD, this.check.state(), c[0]);
			assertTrue(text(this.window.sentenceLabel()).contains("practice.prerun.verdict." + c[0]), text(this.window.sentenceLabel()));
			assertEquals(c[1], text(this.window.adviceLabel()), "the advice of the level panel, not a copy");
			assertTrue(visible(this.window.continueButton()) && visible(this.window.checkAgainButton()), "continue anyway and check again after a bad strum");
			assertEquals(0, this.finished.get(), "the mode does not begin");
		}
		open(State.STRUM);
		this.source.boost = true;
		this.source.reading = readingOf(StrumVerdict.CLIPPING, 0.02, -6.0);
		this.window.refresh();
		assertTrue(text(this.window.adviceLabel()).contains("practice.gain.warning.BOOST_CAUSES_CLIPPING"), "a clip made by the application's own boost says so: " + text(this.window.adviceLabel()));
		assertTrue(text(this.window.adviceLabel()).contains("practice.level.guidance.CLIPPING"));
	}

	@Test
	public void aGoodStrumGoesOnAtOnceAndClosesTheWindow() {
		open(State.STRUM);
		this.source.reading = readingOf(StrumVerdict.GOOD, 0.0, -20.0);
		this.window.refresh();
		assertEquals(State.READY, this.check.state());
		assertFalse(this.check.continuedAnyway());
		assertEquals(1, this.finished.get(), "the window gives the hand back once");
		this.window.refresh();
		assertEquals(1, this.finished.get(), "and only once");
		assertEquals(1, this.source.closed, "its listeners are released");
	}

	@Test
	public void continueAnywayIsOfferedAfterTenSecondsWithoutAStrumAndGoesOnForThisStartOnly() {
		open(State.STRUM);
		this.now += 9 * SECOND;
		this.window.refresh();
		assertFalse(visible(this.window.continueButton()), "not yet");
		this.now += 2 * SECOND;
		this.window.refresh();
		assertTrue(visible(this.window.continueButton()), "ten seconds without a strum");
		SwtContractToolkit.userSelects(this.window.continueButton());
		assertEquals(State.READY, this.check.state());
		assertTrue(this.check.continuedAnyway());
		assertEquals(1, this.finished.get());
	}

	@Test
	public void checkAgainStartsTheMeterAgainAndGoesBackToTheStrum() {
		open(State.STRUM);
		this.source.reading = readingOf(StrumVerdict.TOO_WEAK, 0.0, -50.0);
		this.window.refresh();
		assertEquals(State.LEVEL_BAD, this.check.state());
		int before = this.source.resets.get();
		SwtContractToolkit.userSelects(this.window.checkAgainButton());
		assertEquals(before + 1, this.source.resets.get(), "the meter starts again: the old strum does not count");
		assertEquals(State.STRUM, this.check.state());
		this.window.refresh();
		assertTrue(text(this.window.sentenceLabel()).contains("practice.prerun.verdict.NO_STRUM"));
		assertFalse(visible(this.window.checkAgainButton()), "nothing to check again until a new strum is judged");
	}

	@Test
	public void cancelTheCloseButtonAndEscapeCancelTheStartAndAGoodStrumIsNotUndoneByTheClose() {
		for (int way = 0; way < 3; way++) {
			this.finished.set(0);
			open(State.STRUM);
			if (way == 0) {
				SwtContractToolkit.userSelects(this.window.cancelButton());
			} else if (way == 1) {
				this.window.windowClosed(); // the close button of the window
			} else {
				this.window.escapePressed();
			}
			assertEquals(State.CANCELLED, this.check.state(), "way " + way);
			assertEquals(1, this.finished.get(), "the flow is told once, so that a new start is accepted at once");
		}
		this.finished.set(0);
		open(State.STRUM);
		this.source.reading = readingOf(StrumVerdict.GOOD, 0.0, -20.0);
		this.window.refresh();
		this.window.windowClosed(); // the window closes itself after READY
		assertEquals(State.READY, this.check.state(), "closing a window that was ready does not cancel");
		assertEquals(1, this.finished.get());
	}
}
