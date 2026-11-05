package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import app.tuxguitar.ui.layout.UITableLayout;
import app.tuxguitar.ui.widget.UIPanel;

/**
 * The fixed bottom area of the practice window (spec 025, FR-002 to FR-004, contracts/practice-window.md): the line of a greyed start, visible only
 * while the start is greyed, and Check again only for the causes of the input level.
 */
public class StartAreaTest {

	private final SwtContractToolkit toolkit = new SwtContractToolkit();
	private final AtomicInteger checks = new AtomicInteger();

	private StartArea open() {
		UIPanel root = (UIPanel) this.toolkit.create(UIPanel.class, null);
		root.setLayout(new UITableLayout());
		return new StartArea(this.toolkit.factory(), root, key -> "<" + key + ">", this.checks::incrementAndGet);
	}

	private static boolean visible(Object widget) {
		return SwtContractToolkit.state(widget).properties.get("Visible") == Boolean.TRUE;
	}

	@Test
	public void aLevelCauseShowsItsLineAndCheckAgain() {
		StartArea area = open();
		area.show(new QuickStart.Blocker("practice.start.blocker.no-signal", true));
		assertTrue(visible(area.getLine()));
		assertTrue(visible(area.getCheckAgain()));
		assertEquals("<practice.start.check-again>", area.getCheckAgain().getText());
	}

	@Test
	public void otherCausesShowTheLineWithoutCheckAgain() {
		StartArea area = open();
		area.show(new QuickStart.Blocker("practice.start.blocker.no-track", false));
		assertTrue(visible(area.getLine()));
		assertFalse(visible(area.getCheckAgain()));
	}

	@Test
	public void noBlockerHidesBoth() {
		StartArea area = open();
		area.show(new QuickStart.Blocker("practice.start.blocker.no-signal", true));
		area.show(null);
		assertFalse(visible(area.getLine()));
		assertFalse(visible(area.getCheckAgain()));
	}

	@Test
	public void checkAgainAsksForACheck() {
		StartArea area = open();
		area.show(new QuickStart.Blocker("practice.start.blocker.input-lost", true));
		SwtContractToolkit.userSelects(area.getCheckAgain());
		assertEquals(1, this.checks.get());
	}
}
