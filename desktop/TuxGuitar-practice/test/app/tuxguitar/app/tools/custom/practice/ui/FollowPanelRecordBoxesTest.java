package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.session.FollowSettings;
import app.tuxguitar.ui.layout.UITableLayout;
import app.tuxguitar.ui.widget.UIPanel;

/**
 * Spec 016 (T011, FR-003): the two boxes of the record of a Follow run, on the headless toolkit. Shown in Follow only; the copy of the sound is
 * offered only with the record; both are off for new settings, so off at each launch.
 */
public class FollowPanelRecordBoxesTest {

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

	private static boolean enabled(Object widget) {
		return SwtContractToolkit.state(widget).properties.get("Enabled") == Boolean.TRUE;
	}

	private void chooseMode(FollowSettings.Mode mode) {
		this.panel.modeSelect().setSelectedValue(mode);
		SwtContractToolkit.userSelects(this.panel.modeSelect());
	}

	private static void tick(Object box, boolean on) {
		SwtContractToolkit.state(box).properties.put("Selected", on);
		SwtContractToolkit.userSelects(box);
	}

	@Test
	public void bothAreOffForNewSettings() {
		assertFalse(this.settings.isRecordRun());
		assertFalse(this.settings.isRecordAudio());
	}

	@Test
	public void theBoxesAreShownInFollowOnly() {
		open();
		assertFalse(visible(this.panel.recordRunBox()), "hidden in Wait");
		assertFalse(visible(this.panel.recordAudioBox()));
		chooseMode(FollowSettings.Mode.FOLLOW);
		assertTrue(visible(this.panel.recordRunBox()));
		assertTrue(visible(this.panel.recordAudioBox()));
		chooseMode(FollowSettings.Mode.ARCADE);
		assertFalse(visible(this.panel.recordRunBox()), "hidden in Arcade");
		assertFalse(visible(this.panel.recordAudioBox()));
	}

	@Test
	public void theSoundIsOfferedOnlyWithTheRecord() {
		open();
		chooseMode(FollowSettings.Mode.FOLLOW);
		assertFalse(enabled(this.panel.recordAudioBox()), "disabled until the record is ticked");
		tick(this.panel.recordRunBox(), true);
		assertTrue(this.settings.isRecordRun());
		assertTrue(enabled(this.panel.recordAudioBox()));
		tick(this.panel.recordAudioBox(), true);
		assertTrue(this.settings.isRecordAudio());
		tick(this.panel.recordRunBox(), false);
		assertFalse(this.settings.isRecordRun());
		assertFalse(enabled(this.panel.recordAudioBox()));
		assertFalse(this.settings.isRecordAudio(), "no sound without the record");
	}

	@Test
	public void theTextsAreTheOnesOfTheLanguageFiles() {
		open();
		assertEquals("practice.follow.record.run", SwtContractToolkit.state(this.panel.recordRunBox()).properties.get("Text"));
		assertEquals("practice.follow.record.audio", SwtContractToolkit.state(this.panel.recordAudioBox()).properties.get("Text"));
	}
}
