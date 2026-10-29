package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.session.AudioRoute;
import app.tuxguitar.util.TGContext;

/**
 * Spec 016 (T017, FR-004): the routes for which the learner chose to go on without a rhythm calibration, for this launch only. One set per application
 * context, in memory; another input, output or format is another route; a null route is never declined.
 */
public class DeclinedRoutesTest {

	private static final AudioRoute ROCKSMITH = new AudioRoute("Rocksmith USB Guitar Adapter", "MacBook Speakers", 48000, "1ch 16bit");
	private static final AudioRoute OTHER_OUTPUT = new AudioRoute("Rocksmith USB Guitar Adapter", "Headphones", 48000, "1ch 16bit");

	@Test
	public void aDeclinedRouteIsRememberedAndNoOther() {
		DeclinedRoutes declined = new DeclinedRoutes();
		assertFalse(declined.isDeclined(ROCKSMITH));
		declined.decline(ROCKSMITH);
		assertTrue(declined.isDeclined(ROCKSMITH));
		assertTrue(declined.isDeclined(new AudioRoute("Rocksmith USB Guitar Adapter", "MacBook Speakers", 48000, "1ch 16bit")), "the same route");
		assertFalse(declined.isDeclined(OTHER_OUTPUT), "another output is another route");
	}

	@Test
	public void aNullRouteIsNeverDeclined() {
		DeclinedRoutes declined = new DeclinedRoutes();
		declined.decline(null);
		assertFalse(declined.isDeclined(null));
	}

	@Test
	public void oneSetPerContextAndANewContextIsANewLaunch() {
		TGContext context = new TGContext();
		DeclinedRoutes.of(context).decline(ROCKSMITH);
		assertSame(DeclinedRoutes.of(context), DeclinedRoutes.of(context));
		assertTrue(DeclinedRoutes.of(context).isDeclined(ROCKSMITH));
		assertFalse(DeclinedRoutes.of(new TGContext()).isDeclined(ROCKSMITH));
	}
}
