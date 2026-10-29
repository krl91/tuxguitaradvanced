package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.session.FollowSettings;
import app.tuxguitar.app.tools.custom.practice.session.PlayTimeWindow;
import app.tuxguitar.ui.layout.UITableLayout;
import app.tuxguitar.ui.widget.UIPanel;

/**
 * Spec 016 (T028, US3): in Follow, the slider <i>Time to play the note</i> (100 to 600 ms) takes the place of the timing window, which stays for Arcade only.
 * On the headless toolkit, as {@code FollowPanelTest} does it.
 */
public class FollowPanelPlayTimeTest {

	private final SwtContractToolkit toolkit = new SwtContractToolkit();
	private final FollowSettings settings = FollowPanelTest.settings();
	private FollowPanel panel;

	private FollowPanel open() {
		UIPanel root = (UIPanel) this.toolkit.create(UIPanel.class, null);
		root.setLayout(new UITableLayout());
		this.panel = new FollowPanel(this.toolkit.factory(), root, this.settings, new FollowPanelTest.FakeBackend());
		return this.panel;
	}

	private static boolean visible(Object widget) {
		return SwtContractToolkit.state(widget).properties.get("Visible") == Boolean.TRUE;
	}

	private void chooseMode(FollowSettings.Mode mode) {
		this.panel.modeSelect().setSelectedValue(mode);
		SwtContractToolkit.userSelects(this.panel.modeSelect());
	}

	private void choosePlayTime(int millis) {
		this.panel.playTimeSlider().setValue(millis);
		SwtContractToolkit.userSelects(this.panel.playTimeSlider());
	}

	@Test
	public void followShowsTheTimeToPlayAndArcadeTheTimingWindow() {
		open();
		assertFalse(visible(this.panel.playTimeSlider()), "the Wait mode has no tempo");
		assertFalse(visible(this.panel.windowSlider()));
		chooseMode(FollowSettings.Mode.FOLLOW);
		assertTrue(visible(this.panel.playTimeSlider()));
		assertFalse(visible(this.panel.windowSlider()), "the timing window is Arcade's now");
		chooseMode(FollowSettings.Mode.ARCADE);
		assertFalse(visible(this.panel.playTimeSlider()));
		assertTrue(visible(this.panel.windowSlider()), "Arcade keeps its window");
	}

	@Test
	public void theSliderGoesFromOneHundredToSixHundredAndShowsTheSavedTime() {
		this.settings.setPlayTimeMillis(450);
		open();
		chooseMode(FollowSettings.Mode.FOLLOW);
		assertEquals(PlayTimeWindow.MIN_MILLIS, this.panel.playTimeSlider().getMinimum());
		assertEquals(PlayTimeWindow.MAX_MILLIS, this.panel.playTimeSlider().getMaximum());
		assertEquals(450, this.panel.playTimeSlider().getValue(), "the saved time, not a default");
		this.settings.setPlayTimeMillis(200); // the Configuration window changed the same setting
		this.panel.refresh();
		assertEquals(200, this.panel.playTimeSlider().getValue());
	}

	@Test
	public void theTimeTheLearnerPicksIsTheSettingAndMovesNothingElse() {
		this.settings.setWindowMillis(130);
		open();
		chooseMode(FollowSettings.Mode.FOLLOW);
		choosePlayTime(350);
		assertEquals(350, this.settings.getPlayTimeMillis());
		assertEquals(130, this.settings.getWindowMillis(), "Arcade's window does not move");
		assertTrue(this.panel.playTimeHintLabel().getText().contains("practice.follow.play-time.value"));
		chooseMode(FollowSettings.Mode.WAIT);
		chooseMode(FollowSettings.Mode.FOLLOW);
		assertEquals(350, this.panel.playTimeSlider().getValue(), "still there after a visit to the wait mode");
	}

	@Test
	public void theTimeToPlayHasItsTextsInTheThreeLanguages() throws java.io.IOException {
		for (String file : new String[] { "messages.properties", "messages_fr.properties", "messages_es.properties" }) {
			java.util.Properties properties = FollowReasonStringsTest.load(file);
			for (String key : new String[] { "practice.follow.play-time", "practice.follow.play-time.hint" }) {
				String text = properties.getProperty(key);
				assertTrue(text != null && !text.trim().isEmpty() && FollowReasonStringsTest.placeholders(text).isEmpty(), file + " " + key);
			}
			assertTrue(FollowReasonStringsTest.placeholders(properties.getProperty("practice.follow.play-time.value")).contains("0"), file + " shows the time");
		}
	}
}
