package app.tuxguitar.app.tools.custom.practice.ui;

import app.tuxguitar.audio.engine.calibration.DeviceKey;
import app.tuxguitar.audio.engine.calibration.FrontEndState;
import app.tuxguitar.audio.engine.calibration.ProfileLibrary;
import app.tuxguitar.audio.engine.calibration.ProfileState;
import app.tuxguitar.audio.engine.calibration.RecognitionProfile;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.event.UISelectionEvent;
import app.tuxguitar.ui.event.UISelectionListener;
import app.tuxguitar.ui.layout.UITableLayout;
import app.tuxguitar.ui.resource.UIColor;
import app.tuxguitar.ui.widget.UIButton;
import app.tuxguitar.ui.widget.UIContainer;
import app.tuxguitar.ui.widget.UILabel;
import app.tuxguitar.ui.widget.UIPanel;

/**
 * The dot next to the device chooser (spec 013, FR-008, FR-019): a coloured dot (grey, orange, green, red), its text, the warning when the noise reduction or
 * the gain is not what it was at the recording, and the two buttons <i>Calibrate the recognition</i> and <i>Forget the calibration</i>. It is a pure function
 * of the selected device, the library and the state of the input, recomputed by {@link #refresh()} (at every change of input, when the tab is shown, when the
 * noise reduction changes, after a calibration); it never changes the recognition itself, the engine does it from the same library.
 */
final class ProfileDot {

	private static final String P = "practice.profile.";

	/** What the dot reads from the application. */
	interface Host {
		/** The device selected, or null when no input is selected. */
		DeviceKey selectedKey();

		/** The capture rate in use, 0 when not known. */
		int sampleRate();

		/** The gain and the noise reduction in force now. */
		FrontEndState frontEnd();

		/** Asks "Forget the calibration of this device?" and runs {@code yes} (on the interface thread) only when the player confirms. */
		void confirmForget(Runnable yes);

		/** Opens the calibration of the selected device. */
		void calibrate();
	}

	private final CalibrationWindow.Texts texts;
	private final ProfileLibrary library;
	private final Host host;
	private final UIPanel row;
	private final UILabel dot;
	private final UILabel text;
	private final UILabel warning;
	private final UIButton calibrate;
	private final UIButton forget;
	private final UIColor grey;
	private final UIColor orange;
	private final UIColor green;
	private final UIColor red;
	private ProfileState state = ProfileState.NONE;
	private Runnable contentListener;

	ProfileDot(UIFactory factory, UIContainer parent, CalibrationWindow.Texts texts, ProfileLibrary library, Host host) {
		this.texts = texts;
		this.library = library;
		this.host = host;
		this.grey = factory.createColor(150, 150, 150);
		this.orange = factory.createColor(230, 140, 0);
		this.green = factory.createColor(0, 150, 0);
		this.red = factory.createColor(210, 0, 0);
		this.row = factory.createPanel(parent, false);
		UITableLayout layout = new UITableLayout();
		layout.set(UITableLayout.IGNORE_INVISIBLE, Boolean.TRUE);
		this.row.setLayout(layout);

		this.dot = factory.createLabel(this.row);
		this.dot.setText("●");
		layout.set(this.dot, 1, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);
		this.text = factory.createLabel(this.row);
		layout.set(this.text, 1, 2, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, true, false);

		this.calibrate = factory.createButton(this.row);
		this.calibrate.setText(texts.get(P + "dot.calibrate"));
		this.calibrate.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				ProfileDot.this.host.calibrate();
			}
		});
		layout.set(this.calibrate, 1, 3, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, false, false);
		this.forget = factory.createButton(this.row);
		this.forget.setText(texts.get(P + "dot.forget"));
		this.forget.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				forget();
			}
		});
		layout.set(this.forget, 1, 4, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, false, false);

		this.warning = factory.createWrapLabel(this.row);
		layout.set(this.warning, 2, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false, 1, 4);
		layout.set(this.warning, UITableLayout.PACKED_WIDTH, InputSelectorPanel.WRAP_WIDTH);
		refresh();
	}

	UIPanel control() {
		return this.row;
	}

	/** Called after the content changed size, so the window can refit. */
	void setContentListener(Runnable contentListener) {
		this.contentListener = contentListener;
	}

	ProfileState state() {
		return this.state;
	}

	UILabel dotLabel() {
		return this.dot;
	}

	UILabel textLabel() {
		return this.text;
	}

	UILabel warningLabel() {
		return this.warning;
	}

	UIButton calibrateButton() {
		return this.calibrate;
	}

	UIButton forgetButton() {
		return this.forget;
	}

	/** Recomputes the dot, its text and its warning from the selected device, its profile and the state of the input (UI thread). */
	void refresh() {
		DeviceKey key = this.host.selectedKey();
		RecognitionProfile profile = key == null ? null : this.library.get(key);
		int rate = this.host.sampleRate();
		this.state = ProfileState.of(key != null, profile, rate);
		switch (this.state) {
		case NONE:
			show(this.grey, t("dot.none"));
			break;
		case GREEN:
			show(this.green, t("dot.green", percent(profile)));
			break;
		case RED:
			show(this.red, t("dot.red", percent(profile)));
			break;
		default:
			if (profile != null && profile.getOrigin() != RecognitionProfile.Origin.DEFAULT_CHOSEN && !profile.appliesAt(rate)) {
				show(this.orange, t("dot.other-rate", Integer.toString(profile.getSampleRate()), Integer.toString(rate)));
			} else {
				show(this.orange, t("dot.orange"));
			}
		}
		String warningText = this.state == ProfileState.GREEN || this.state == ProfileState.RED ? warning(profile) : null;
		this.warning.setText(warningText == null ? "" : warningText);
		this.warning.setVisible(warningText != null);
		this.calibrate.setEnabled(key != null);
		this.forget.setEnabled(profile != null);
		this.row.layout();
		if (this.contentListener != null) {
			this.contentListener.run();
		}
	}

	private String warning(RecognitionProfile profile) {
		FrontEndState now = this.host.frontEnd();
		FrontEndState then = profile.getFrontEnd();
		if (now == null || then == null) {
			return null;
		}
		if (now.isNoiseReduction() != then.isNoiseReduction()) {
			return t("dot.warning.noise", t(then.isNoiseReduction() ? "dot.on" : "dot.off"));
		}
		if (now.getGainMultiplier() != then.getGainMultiplier()) {
			return t("dot.warning.gain");
		}
		return null;
	}

	private void forget() {
		final DeviceKey key = this.host.selectedKey();
		if (key == null || this.library.get(key) == null) {
			return;
		}
		this.host.confirmForget(() -> {
			// the device that was selected when the question was asked, whatever is selected by the time the answer comes
			this.library.remove(key);
			refresh();
		});
	}

	private void show(UIColor color, String sentence) {
		this.dot.setFgColor(color);
		this.text.setText(sentence);
	}

	private static String percent(RecognitionProfile profile) {
		return Long.toString(Math.round(profile.getFoundRate().totalPercent()));
	}

	private String t(String key, String... arguments) {
		return this.texts.get(P + key, arguments);
	}
}
