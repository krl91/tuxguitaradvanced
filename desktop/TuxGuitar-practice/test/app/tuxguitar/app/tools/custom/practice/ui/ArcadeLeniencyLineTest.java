package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeLeniencyPosition;
import app.tuxguitar.app.tools.custom.practice.session.ArcadeChoices;
import app.tuxguitar.app.tools.custom.practice.session.FollowSettings;
import app.tuxguitar.ui.layout.UITableLayout;
import app.tuxguitar.ui.widget.UIPanel;

/**
 * Spec 014 (T029, FR-002, FR-012): the line of the permissiveness in the Arcade section of the preparation window. It shows the position the player has (the fun
 * one by default), lists the three, is shown for Arcade only, stores a change at once (it applies to the next run), and a stored value that is invalid shows the
 * default. The panel built without the choices (the constructor the existing tests use) has no such line.
 */
public class ArcadeLeniencyLineTest {

	private static final class Memory implements ArcadeChoices.Persistence {
		final Map<String, String> values = new HashMap<String, String>();

		public String get(String key) {
			return this.values.get(key);
		}

		public void set(String key, String value) {
			this.values.put(key, value);
		}
	}

	private final SwtContractToolkit toolkit = new SwtContractToolkit();
	private final Memory file = new Memory();
	private final ArcadeChoices choices = new ArcadeChoices(this.file);

	private FollowPanel open(boolean withChoices) {
		UIPanel root = (UIPanel) this.toolkit.create(UIPanel.class, null);
		root.setLayout(new UITableLayout());
		FollowSettings settings = FollowPanelTest.settings();
		return withChoices ? new FollowPanel(this.toolkit.factory(), root, settings, new FollowPanelTest.FakeBackend(), this.choices)
				: new FollowPanel(this.toolkit.factory(), root, settings, new FollowPanelTest.FakeBackend());
	}

	private static boolean visible(Object widget) {
		return SwtContractToolkit.state(widget).properties.get("Visible") == Boolean.TRUE;
	}

	private static void chooseMode(FollowPanel panel, FollowSettings.Mode mode) {
		panel.modeSelect().setSelectedValue(mode);
		SwtContractToolkit.userSelects(panel.modeSelect());
	}

	@Test
	public void theLineShowsTheThreePositionsWithTheFunOneSelectedByDefault() {
		FollowPanel panel = open(true);
		assertEquals(3, SwtContractToolkit.state(panel.leniencySelect()).items.size());
		assertEquals(ArcadeLeniencyPosition.FUN, panel.leniencySelect().getSelectedValue());
		assertEquals(ArcadeLeniencyPosition.LIKE_PRACTICE, SwtContractToolkit.state(panel.leniencySelect()).items.get(0).getValue());
		assertEquals(ArcadeLeniencyPosition.VERY_PERMISSIVE, SwtContractToolkit.state(panel.leniencySelect()).items.get(2).getValue());
	}

	@Test
	public void theLineIsForArcadeOnly() {
		FollowPanel panel = open(true);
		panel.refresh();
		assertTrue(!visible(panel.leniencySelect()) && !visible(panel.leniencyLabel()), "not in the wait mode");
		chooseMode(panel, FollowSettings.Mode.FOLLOW);
		assertTrue(!visible(panel.leniencySelect()), "not in the follow mode");
		chooseMode(panel, FollowSettings.Mode.ARCADE);
		assertTrue(visible(panel.leniencySelect()) && visible(panel.leniencyLabel()) && visible(panel.leniencyHint()), "in Arcade");
	}

	@Test
	public void changingThePositionStoresItAndNamesWhatItDoes() {
		FollowPanel panel = open(true);
		chooseMode(panel, FollowSettings.Mode.ARCADE);
		panel.leniencySelect().setSelectedValue(ArcadeLeniencyPosition.VERY_PERMISSIVE);
		SwtContractToolkit.userSelects(panel.leniencySelect());
		assertEquals(ArcadeLeniencyPosition.VERY_PERMISSIVE, this.choices.leniency(), "stored at once, read at the next run");
		assertTrue(String.valueOf(SwtContractToolkit.state(panel.leniencyHint()).properties.get("Text")).contains("arcade.leniency.hint.very_permissive"),
				"the hint is the one of the position: " + SwtContractToolkit.state(panel.leniencyHint()).properties.get("Text"));
		panel.leniencySelect().setSelectedValue(ArcadeLeniencyPosition.LIKE_PRACTICE);
		SwtContractToolkit.userSelects(panel.leniencySelect());
		assertEquals(ArcadeLeniencyPosition.LIKE_PRACTICE, this.choices.leniency());
	}

	@Test
	public void aStoredPositionIsShownAndAnInvalidOneShowsTheDefault() {
		this.file.values.put(ArcadeChoices.LENIENCY_KEY, "VERY_PERMISSIVE");
		FollowPanel stored = open(true);
		stored.refresh();
		assertEquals(ArcadeLeniencyPosition.VERY_PERMISSIVE, stored.leniencySelect().getSelectedValue());
		this.file.values.put(ArcadeChoices.LENIENCY_KEY, "RECKLESS");
		FollowPanel invalid = open(true);
		invalid.refresh();
		assertEquals(ArcadeLeniencyPosition.FUN, invalid.leniencySelect().getSelectedValue(), "an invalid saved value falls back to the default");
	}

	@Test
	public void thePanelBuiltWithoutTheChoicesHasNoLine() {
		FollowPanel panel = open(false);
		assertNull(panel.leniencySelect());
		assertNull(panel.leniencyLabel());
	}
}
