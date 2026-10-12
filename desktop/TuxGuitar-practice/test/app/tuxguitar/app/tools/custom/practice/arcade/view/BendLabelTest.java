package app.tuxguitar.app.tools.custom.practice.arcade.view;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.model.PitchPath;

/** The amount of a bend is written in steps (a step is a whole tone), the word translated (spec 003 FR-077). */
public class BendLabelTest {

	private static String label(double... path) {
		return BendLabel.of(BendMark.of(PitchPath.of(path)), SceneFixture.ENGLISH);
	}

	@Test
	public void theAmountIsWrittenInStepsAndAWholeStepIsTheWord() {
		assertEquals("¼", label(0.0, 0.0, 1.0, 0.5));
		assertEquals("½", label(0.0, 0.0, 1.0, 1.0));
		assertEquals("¾", label(0.0, 0.0, 1.0, 1.5));
		assertEquals("full", label(0.0, 0.0, 1.0, 2.0));
		assertEquals("1¼", label(0.0, 0.0, 1.0, 2.5));
		assertEquals("1½", label(0.0, 0.0, 1.0, 3.0));
		assertEquals("2", label(0.0, 0.0, 1.0, 4.0));
		assertEquals("2½", label(0.0, 0.0, 1.0, 5.0));
	}

	@Test
	public void aPreBendHasTheWordBeforeAndAReleaseHasAnArrowAfter() {
		assertEquals("pre full", label(0.0, 2.0, 1.0, 2.0));
		assertEquals("full↓", label(0.0, 0.0, 0.5, 2.0, 1.0, 0.0));
		assertEquals("pre ½↓", label(0.0, 1.0, 0.5, 1.0, 1.0, 0.0));
	}
}
