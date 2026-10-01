package app.tuxguitar.app.tools.custom.practice.ui;

import java.util.Optional;

import app.tuxguitar.app.TuxGuitar;
import app.tuxguitar.audio.engine.AudioEngine;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.frontend.GainScale;
import app.tuxguitar.audio.engine.frontend.GainSuggestion;
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
import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.TGSynchronizer;

/**
 * The input gain of the practice window (spec 002, FR-060…FR-066): a slider in whole decibels with a
 * reset, a warning when the boost mostly amplifies noise or makes the signal clip, and, when the
 * "suggest a gain" setting is on, a suggestion computed from the level check that is applied only
 * when the learner confirms it. The gain lives in the shared engine, so the tuner shows the same value.
 */
public final class GainControl {

	private final TGContext context;
	private final GainBackend backend;
	private final UIPanel panel;
	private final UIScale slider;
	private final UILabel valueLabel;
	private final UIButton resetButton;
	private final UIButton suggestButton;
	private final UIButton applyButton;
	private final UILabel suggestionLabel;
	private final UILabel warningLabel;
	private Runnable stopListening;
	private GainSuggestion pending;
	private boolean updating;
	private boolean connected;
	private Runnable contentListener;

	public GainControl(TGContext context, AudioEngine engine, UIFactory uiFactory, UIContainer parent) {
		this(context, new EngineGainBackend(context, engine), uiFactory, parent);
	}

	GainControl(TGContext context, GainBackend backend, UIFactory uiFactory, UIContainer parent) {
		this.context = context;
		this.backend = backend;
		this.panel = uiFactory.createPanel(parent, false);
		UITableLayout layout = new UITableLayout();
		this.panel.setLayout(layout);

		UILabel title = uiFactory.createLabel(this.panel);
		title.setText(TuxGuitar.getProperty("practice.gain.label"));
		layout.set(title, 1, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);
		this.slider = uiFactory.createHorizontalScale(this.panel);
		this.slider.setMinimum(0);
		this.slider.setMaximum(GainScale.POSITIONS);
		this.slider.setIncrement(1);
		layout.set(this.slider, 1, 2, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);
		this.valueLabel = uiFactory.createLabel(this.panel);
		layout.set(this.valueLabel, 1, 3, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);
		layout.set(this.valueLabel, UITableLayout.PACKED_WIDTH, 60f);
		this.resetButton = uiFactory.createButton(this.panel);
		this.resetButton.setText(TuxGuitar.getProperty("practice.gain.reset"));
		layout.set(this.resetButton, 1, 4, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);

		this.warningLabel = uiFactory.createWrapLabel(this.panel);
		layout.set(this.warningLabel, 2, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false, 1, 4);
		layout.set(this.warningLabel, UITableLayout.PACKED_WIDTH, InputSelectorPanel.WRAP_WIDTH);

		this.suggestButton = uiFactory.createButton(this.panel);
		this.suggestButton.setText(TuxGuitar.getProperty("practice.gain.suggest"));
		layout.set(this.suggestButton, 3, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);
		this.suggestionLabel = uiFactory.createWrapLabel(this.panel);
		layout.set(this.suggestionLabel, 3, 2, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false, 1, 2);
		layout.set(this.suggestionLabel, UITableLayout.PACKED_WIDTH, InputSelectorPanel.WRAP_WIDTH - 140f);
		this.applyButton = uiFactory.createButton(this.panel);
		this.applyButton.setText(TuxGuitar.getProperty("practice.gain.apply"));
		layout.set(this.applyButton, 3, 4, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);

		this.slider.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				if( !GainControl.this.updating ) {
					GainControl.this.backend.setGain(GainScale.toMultiplier(GainControl.this.slider.getValue()));
					GainControl.this.refresh();
				}
			}
		});
		this.resetButton.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				GainControl.this.backend.resetGain();
				GainControl.this.refresh();
			}
		});
		this.suggestButton.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				GainControl.this.suggest();
			}
		});
		this.applyButton.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				GainControl.this.apply();
			}
		});
		refresh();
	}

	public UIPanel getControl() {
		return this.panel;
	}


	/** Called after the content changed size, so the window can refit (UI thread). */
	public void setContentListener(Runnable contentListener) {
		this.contentListener = contentListener;
	}

	public void connect() {
		if( !this.connected ) {
			this.connected = true;
			this.stopListening = this.backend.onSettingsChanged(() -> TGSynchronizer.getInstance(this.context).executeLater(this::refresh));
		}
	}

	public void disconnect() {
		if( this.connected ) {
			this.connected = false;
			if( this.stopListening != null ) {
				this.stopListening.run();
				this.stopListening = null;
			}
		}
	}

	/** Shows the current gain (it may have been reset by an input change) and the suggestion controls the setting allows. */
	public void refresh() {
		if( this.panel.isDisposed() ) {
			return;
		}
		this.updating = true;
		try {
			double gain = this.backend.getGain();
			this.slider.setValue(GainScale.toPosition(gain));
			this.valueLabel.setText(GainScale.format(gain));
			boolean suggestions = this.backend.suggestionsEnabled();
			this.suggestButton.setVisible(suggestions);
			this.suggestionLabel.setVisible(suggestions);
			this.applyButton.setVisible(suggestions);
			if( !suggestions ) {
				this.pending = null;
			}
			this.applyButton.setEnabled(this.pending != null && this.pending.getReason() != GainSuggestion.Reason.OK);
		} finally {
			this.updating = false;
		}
		this.panel.layout();
		if( this.contentListener != null ) {
			this.contentListener.run();
		}
	}

	/** The warning for the latest frame (UI thread, about 30 times a second). */
	public void showWarning(AnalysisFrame frame) {
		InputWarning warning = this.backend.warning(frame);
		String text = (warning == InputWarning.NONE ? "" : TuxGuitar.getProperty("practice.gain.warning." + warning.name()));
		if( !text.equals(this.warningLabel.getText()) ) {
			this.warningLabel.setText(text);
			this.panel.layout();
			if( this.contentListener != null ) {
				this.contentListener.run();
			}
		}
	}

	private void suggest() {
		Optional<GainSuggestion> suggestion = this.backend.suggest();
		this.pending = suggestion.orElse(null);
		if( !suggestion.isPresent() ) {
			this.suggestionLabel.setText(TuxGuitar.getProperty("practice.gain.suggestion.NONE"));
		} else {
			GainSuggestion value = suggestion.get();
			this.suggestionLabel.setText(TuxGuitar.getProperty("practice.gain.suggestion." + value.getReason().name(),
					new String[] { GainScale.format(value.getMultiplier()) }));
		}
		refresh();
	}

	private void apply() {
		if( this.pending != null && this.pending.getReason() != GainSuggestion.Reason.OK ) {
			this.backend.setGain(this.pending.getMultiplier());
			this.pending = null;
			this.suggestionLabel.setText("");
			refresh();
		}
	}
}
