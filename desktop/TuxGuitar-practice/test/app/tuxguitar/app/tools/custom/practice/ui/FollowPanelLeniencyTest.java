package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeLeniencyPosition;
import app.tuxguitar.app.tools.custom.practice.session.ArcadeChoices;
import app.tuxguitar.app.tools.custom.practice.session.FollowChoices;
import app.tuxguitar.app.tools.custom.practice.session.FollowLeniencyPosition;
import app.tuxguitar.app.tools.custom.practice.session.FollowSettings;
import app.tuxguitar.ui.layout.UITableLayout;
import app.tuxguitar.ui.widget.UIPanel;

/**
 * Spec 016 (T033, FR-005): the choice of Follow's pitch permissiveness in the Mode tab, on the headless toolkit. Follow only; Permissive when nothing or an
 * unknown name is saved; a choice is saved under its own key, Arcade's untouched; the texts say <i>permissiveness</i> in the three languages.
 */
public class FollowPanelLeniencyTest {

	private final SwtContractToolkit toolkit = new SwtContractToolkit();
	private final FollowSettings settings = FollowPanelTest.settings();
	private final Map<String, String> arcadeStore = new HashMap<String, String>();
	private final Map<String, String> followStore = new HashMap<String, String>();
	private FollowPanel panel;

	private static ArcadeChoices.Persistence persistence(Map<String, String> store) {
		return new ArcadeChoices.Persistence() {
			public String get(String key) {
				return store.get(key);
			}

			public void set(String key, String value) {
				store.put(key, value);
			}
		};
	}

	private FollowPanel open() {
		UIPanel root = (UIPanel) this.toolkit.create(UIPanel.class, null);
		root.setLayout(new UITableLayout());
		this.panel = new FollowPanel(this.toolkit.factory(), root, this.settings, new FollowPanelTest.FakeBackend(),
				new ArcadeChoices(persistence(this.arcadeStore)), new FollowChoices(persistence(this.followStore)));
		return this.panel;
	}

	private static boolean visible(Object widget) {
		return SwtContractToolkit.state(widget).properties.get("Visible") == Boolean.TRUE;
	}

	private void chooseMode(FollowSettings.Mode mode) {
		this.panel.modeSelect().setSelectedValue(mode);
		SwtContractToolkit.userSelects(this.panel.modeSelect());
	}

	@Test
	public void theChoiceIsShownInFollowOnlyAndArcadesInArcadeOnly() {
		open();
		assertFalse(visible(this.panel.followLeniencySelect()), "hidden in Wait");
		chooseMode(FollowSettings.Mode.FOLLOW);
		assertTrue(visible(this.panel.followLeniencySelect()));
		assertFalse(visible(this.panel.leniencySelect()), "Arcade's choice is not Follow's");
		chooseMode(FollowSettings.Mode.ARCADE);
		assertFalse(visible(this.panel.followLeniencySelect()));
		assertTrue(visible(this.panel.leniencySelect()));
	}

	@Test
	public void permissiveWhenNothingOrAnUnknownNameIsSaved() {
		open();
		chooseMode(FollowSettings.Mode.FOLLOW);
		assertEquals(FollowLeniencyPosition.PERMISSIVE, this.panel.followLeniencySelect().getSelectedValue());
		this.followStore.put(FollowChoices.LENIENCY_KEY, "FUN");
		this.panel.refresh();
		assertEquals(FollowLeniencyPosition.PERMISSIVE, this.panel.followLeniencySelect().getSelectedValue());
		assertTrue(this.panel.followLeniencyHintLabel().getText().contains("practice.follow.leniency.hint.permissive"));
	}

	@Test
	public void aChoiceIsSavedUnderItsOwnKeyAndArcadesIsUntouched() {
		this.arcadeStore.put(ArcadeChoices.LENIENCY_KEY, ArcadeLeniencyPosition.VERY_PERMISSIVE.name());
		open();
		chooseMode(FollowSettings.Mode.FOLLOW);
		this.panel.followLeniencySelect().setSelectedValue(FollowLeniencyPosition.STRICT);
		SwtContractToolkit.userSelects(this.panel.followLeniencySelect());
		assertEquals("practice.follow.leniency", FollowChoices.LENIENCY_KEY);
		assertEquals("STRICT", this.followStore.get(FollowChoices.LENIENCY_KEY));
		assertEquals(FollowLeniencyPosition.STRICT, new FollowChoices(persistence(this.followStore)).leniency(), "kept for the next run");
		assertEquals(ArcadeLeniencyPosition.VERY_PERMISSIVE.name(), this.arcadeStore.get(ArcadeChoices.LENIENCY_KEY));
		assertNull(this.arcadeStore.get(FollowChoices.LENIENCY_KEY));
		assertTrue(this.panel.followLeniencyHintLabel().getText().contains("practice.follow.leniency.hint.strict"));
	}

	@Test
	public void anUnwritableStoreNeverThrows() {
		FollowChoices broken = new FollowChoices(new ArcadeChoices.Persistence() {
			public String get(String key) {
				throw new IllegalStateException("unreadable");
			}

			public void set(String key, String value) {
				throw new IllegalStateException("unwritable");
			}
		});
		assertEquals(FollowLeniencyPosition.PERMISSIVE, broken.leniency());
		assertFalse(broken.setLeniency(FollowLeniencyPosition.STRICT));
	}

	@Test
	public void theTextsSayPermissivenessInTheThreeLanguages() throws IOException {
		String[] files = { "messages.properties", "messages_fr.properties", "messages_es.properties" };
		String[] words = { "ermissiveness", "ermissivité", "ermisividad" };
		for (int f = 0; f < files.length; f++) {
			Properties properties = FollowReasonStringsTest.load(files[f]);
			assertTrue(properties.getProperty("practice.follow.leniency.label").contains(words[f]), files[f]);
			for (FollowLeniencyPosition position : FollowLeniencyPosition.values()) {
				String name = position.name().toLowerCase(java.util.Locale.ROOT);
				for (String key : new String[] { "practice.follow.leniency." + name, "practice.follow.leniency.hint." + name }) {
					String text = properties.getProperty(key);
					assertTrue(text != null && !text.trim().isEmpty() && FollowReasonStringsTest.placeholders(text).isEmpty(), files[f] + " " + key);
				}
			}
			String line = properties.getProperty("practice.follow.summary.settings");
			assertTrue(line != null && FollowReasonStringsTest.placeholders(line).contains("0") && FollowReasonStringsTest.placeholders(line).contains("1"),
					files[f] + ": the summary names the position and the time to play");
		}
	}
}
