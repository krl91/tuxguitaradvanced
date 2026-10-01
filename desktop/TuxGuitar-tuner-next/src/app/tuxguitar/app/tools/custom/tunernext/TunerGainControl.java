package app.tuxguitar.app.tools.custom.tunernext;

import app.tuxguitar.app.TuxGuitar;
import app.tuxguitar.audio.engine.AudioEngine;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.frontend.GainScale;
import app.tuxguitar.audio.engine.frontend.InputWarning;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.event.UISelectionEvent;
import app.tuxguitar.ui.event.UISelectionListener;
import app.tuxguitar.ui.layout.UITableLayout;
import app.tuxguitar.ui.widget.UIButton;
import app.tuxguitar.ui.widget.UIContainer;
import app.tuxguitar.ui.widget.UILabel;
import app.tuxguitar.ui.widget.UIPanel;
import app.tuxguitar.ui.widget.UIScale;

/**
 * The input gain in the tuner (spec 002, FR-060): the same shared engine gain as in the practice
 * window, as a slider in whole decibels with a reset and the warning about a boost that mostly
 * amplifies noise or makes the signal clip. It lets the learner raise a weak input while tuning.
 */
final class TunerGainControl {

	private final AudioEngine engine;
	private final UIPanel panel;
	private final UIScale slider;
	private final UILabel valueLabel;
	private final UILabel warningLabel;
	private boolean updating;

	TunerGainControl(AudioEngine engine, UIFactory uiFactory, UIContainer parent) {
		this.engine = engine;
		this.panel = uiFactory.createPanel(parent, false);
		UITableLayout layout = new UITableLayout();
		this.panel.setLayout(layout);

		UILabel title = uiFactory.createLabel(this.panel);
		title.setText(TuxGuitar.getProperty("tunernext.gain.label"));
		layout.set(title, 1, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);
		this.slider = uiFactory.createHorizontalScale(this.panel);
		this.slider.setMinimum(0);
		this.slider.setMaximum(GainScale.POSITIONS);
		this.slider.setIncrement(1);
		layout.set(this.slider, 1, 2, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);
		this.valueLabel = uiFactory.createLabel(this.panel);
		layout.set(this.valueLabel, 1, 3, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);
		layout.set(this.valueLabel, UITableLayout.PACKED_WIDTH, 60f);
		UIButton reset = uiFactory.createButton(this.panel);
		reset.setText(TuxGuitar.getProperty("tunernext.gain.reset"));
		layout.set(reset, 1, 4, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);
		this.warningLabel = uiFactory.createWrapLabel(this.panel);
		layout.set(this.warningLabel, 2, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false, 1, 4);
		layout.set(this.warningLabel, UITableLayout.PACKED_WIDTH, 380f);

		this.slider.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				if( !TunerGainControl.this.updating ) {
					TunerGainControl.this.engine.setInputGain(GainScale.toMultiplier(TunerGainControl.this.slider.getValue()));
					TunerGainControl.this.refresh();
				}
			}
		});
		reset.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				TunerGainControl.this.engine.resetInputGain();
				TunerGainControl.this.refresh();
			}
		});
		refresh();
	}

	UIPanel getControl() {
		return this.panel;
	}

	/** Shows the current gain (an input change resets it). */
	void refresh() {
		if( this.panel.isDisposed() ) {
			return;
		}
		this.updating = true;
		try {
			double gain = this.engine.getInputGain();
			this.slider.setValue(GainScale.toPosition(gain));
			this.valueLabel.setText(GainScale.format(gain));
		} finally {
			this.updating = false;
		}
	}

	/** The warning for the latest frame (UI thread). Returns true when the text changed, so the window can refit. */
	boolean showWarning(AnalysisFrame frame) {
		InputWarning warning = this.engine.getInputWarning(frame);
		String text = (warning == InputWarning.NONE ? "" : TuxGuitar.getProperty("tunernext.gain.warning." + warning.name()));
		if( text.equals(this.warningLabel.getText()) ) {
			return false;
		}
		this.warningLabel.setText(text);
		this.panel.layout();
		return true;
	}
}
