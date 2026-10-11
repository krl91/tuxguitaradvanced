package app.tuxguitar.app.tools.custom.practice.ui;

import java.util.ArrayList;
import java.util.List;

import app.tuxguitar.app.TuxGuitar;
import app.tuxguitar.app.tools.custom.practice.session.InputDefaultPolicy;
import app.tuxguitar.app.tools.custom.practice.session.InputDefaultStore;
import app.tuxguitar.audio.engine.AudioEngine;
import app.tuxguitar.audio.engine.input.AudioInputDescriptor;
import app.tuxguitar.audio.engine.input.CurrentInputSelection;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.event.UISelectionEvent;
import app.tuxguitar.ui.event.UISelectionListener;
import app.tuxguitar.ui.layout.UITableLayout;
import app.tuxguitar.ui.widget.UIButton;
import app.tuxguitar.ui.widget.UIContainer;
import app.tuxguitar.ui.widget.UIDropDownSelect;
import app.tuxguitar.ui.widget.UILabel;
import app.tuxguitar.ui.widget.UIPanel;
import app.tuxguitar.ui.widget.UISelectItem;
import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.TGSynchronizer;

/**
 * The shared input/channel selector (FR-035, FR-039): one entry per mono device and per usable
 * channel of multi-channel devices. Choosing an entry makes it the current input of the tuner and
 * practice, which starts a new level check. Never switches input by itself; when the selected
 * channel of a multi-channel device has no signal it only suggests trying another channel.
 */
public final class InputSelectorPanel {

	/** Width at which long hints wrap, so the window never grows sideways off the display. */
	static final float WRAP_WIDTH = 440f;

	private final TGContext context;
	private final AudioEngine engine;
	private final UIPanel panel;
	private final UIDropDownSelect<AudioInputDescriptor> inputs;
	private final UILabel hint;
	private final CurrentInputSelection.Listener selectionListener;
	private boolean updating;
	/** The remembered default input was in the previous reading of the list (it is selected again when it appears after an absence). */
	private boolean rememberedWasThere;
	private Runnable contentListener;

	public InputSelectorPanel(TGContext context, AudioEngine engine, UIFactory uiFactory, UIContainer parent) {
		this.context = context;
		this.engine = engine;
		this.panel = uiFactory.createPanel(parent, false);
		UITableLayout layout = new UITableLayout();
		this.panel.setLayout(layout);

		UILabel label = uiFactory.createLabel(this.panel);
		label.setText(TuxGuitar.getProperty("practice.input"));
		layout.set(label, 1, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);

		this.inputs = uiFactory.createDropDownSelect(this.panel);
		layout.set(this.inputs, 1, 2, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);
		this.inputs.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				if( !InputSelectorPanel.this.updating ) {
					select(InputSelectorPanel.this.inputs.getSelectedValue());
				}
			}
		});

		// an interface plugged in while the application runs: the learner asks for the list again (the list is not watched)
		UIButton refresh = uiFactory.createButton(this.panel);
		refresh.setText(TuxGuitar.getProperty("practice.input.refresh"));
		refresh.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				reload();
			}
		});
		layout.set(refresh, 1, 3, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, false, false);

		// the learner's default input: kept in the configuration, taken back when it is there (it replaces the one that was remembered)
		UIButton remember = uiFactory.createButton(this.panel);
		remember.setText(TuxGuitar.getProperty("practice.input.remember"));
		remember.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				remember();
			}
		});
		layout.set(remember, 1, 4, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, false, false);

		this.hint = uiFactory.createWrapLabel(this.panel);
		layout.set(this.hint, 2, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false, 1, 4);
		layout.set(this.hint, UITableLayout.PACKED_WIDTH, WRAP_WIDTH);

		this.selectionListener = snapshot -> TGSynchronizer.getInstance(this.context).executeLater(() -> {
			if( !this.panel.isDisposed() ) {
				updateHint(snapshot);
			}
		});
		this.engine.getSelection().addListener(this.selectionListener);
		reload();
	}

	/** Called after the panel's content changed size, so the window can refit (UI thread). */
	public void setContentListener(Runnable contentListener) {
		this.contentListener = contentListener;
	}

	public UIPanel getControl() {
		return this.panel;
	}

	/** Lists the inputs again; offers the first one (system default first) if none is selected. */
	public void reload() {
		List<AudioInputDescriptor> available = this.engine.getCatalog().listInputs();
		CurrentInputSelection.Snapshot snapshot = this.engine.getSelection().snapshot();
		AudioInputDescriptor current = null;
		String remembered = InputDefaultStore.get(this.context);
		List<String> ids = new ArrayList<String>();
		for( AudioInputDescriptor input : available ) {
			ids.add(input.getId());
		}
		String currentId = snapshot.getInput() != null ? snapshot.getInput().getId() : null;
		String chosen = InputDefaultPolicy.choose(ids, currentId, remembered, this.rememberedWasThere);
		this.rememberedWasThere = remembered != null && ids.contains(remembered);
		this.updating = true;
		try {
			this.inputs.removeItems();
			for( AudioInputDescriptor input : available ) {
				this.inputs.addItem(new UISelectItem<AudioInputDescriptor>(input.getDisplayName(), input));
				if( snapshot.getInput() != null && input.getId().equals(snapshot.getInput().getId()) ) {
					current = input;
				}
			}
			if( current != null ) {
				this.inputs.setSelectedValue(current);
			}
			for( AudioInputDescriptor input : available ) {
				if( chosen != null && input.getId().equals(chosen) ) {
					this.inputs.setSelectedValue(input);
				}
			}
		} finally {
			this.updating = false;
		}
		if( chosen != null ) {
			for( AudioInputDescriptor input : available ) {
				if( input.getId().equals(chosen) ) {
					select(input);
					return;
				}
			}
		}
		if( available.isEmpty() ) {
			this.hint.setText(TuxGuitar.getProperty("practice.input.none"));
			contentChanged();
		} else if( current == null ) {
			AudioInputDescriptor selected = this.engine.selectDefaultInputIfNone();
			for( AudioInputDescriptor input : available ) {
				if( selected != null && input.getId().equals(selected.getId()) ) {
					this.inputs.setSelectedValue(input);
				}
			}
		} else {
			updateHint(snapshot);
		}
	}

	/** The input shown becomes the default input of the learner, over the one that was remembered. */
	private void remember() {
		AudioInputDescriptor input = this.inputs.getSelectedValue();
		if( input != null ) {
			InputDefaultStore.set(this.context, input.getId());
			this.rememberedWasThere = true;
			this.hint.setText(TuxGuitar.getProperty("practice.input.remembered", new String[] { input.getDisplayName() }));
			contentChanged();
		}
	}

	public void dispose() {
		this.engine.getSelection().removeListener(this.selectionListener);
	}

	private void select(AudioInputDescriptor input) {
		if( input != null ) {
			this.engine.getSelection().select(input, null);
		}
	}

	private void updateHint(CurrentInputSelection.Snapshot snapshot) {
		InputAssessment assessment = snapshot.getAssessment();
		boolean silentChannel = (snapshot.getInput() != null && snapshot.getInput().getChannelCount() > 1
				&& assessment.isUnusable() && assessment.getReason() == InputAssessment.Reason.NO_SIGNAL);
		this.hint.setText(silentChannel ? TuxGuitar.getProperty("practice.input.try-another-channel") : "");
		contentChanged();
	}

	private void contentChanged() {
		this.panel.layout();
		if( this.contentListener != null ) {
			this.contentListener.run();
		}
	}
}
