package app.tuxguitar.app.tools.custom.practice.ui;

import java.util.List;

import app.tuxguitar.app.TuxGuitar;
import app.tuxguitar.audio.engine.AudioEngine;
import app.tuxguitar.audio.engine.input.AudioInputDescriptor;
import app.tuxguitar.audio.engine.input.CurrentInputSelection;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.event.UISelectionEvent;
import app.tuxguitar.ui.event.UISelectionListener;
import app.tuxguitar.ui.layout.UITableLayout;
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

	private final TGContext context;
	private final AudioEngine engine;
	private final UIPanel panel;
	private final UIDropDownSelect<AudioInputDescriptor> inputs;
	private final UILabel hint;
	private final CurrentInputSelection.Listener selectionListener;
	private boolean updating;

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

		this.hint = uiFactory.createLabel(this.panel);
		layout.set(this.hint, 2, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false, 1, 2);

		this.selectionListener = snapshot -> TGSynchronizer.getInstance(this.context).executeLater(() -> {
			if( !this.panel.isDisposed() ) {
				updateHint(snapshot);
			}
		});
		this.engine.getSelection().addListener(this.selectionListener);
		reload();
	}

	public UIPanel getControl() {
		return this.panel;
	}

	/** Lists the inputs again; offers the first one (system default first) if none is selected. */
	public void reload() {
		List<AudioInputDescriptor> available = this.engine.getCatalog().listInputs();
		CurrentInputSelection.Snapshot snapshot = this.engine.getSelection().snapshot();
		AudioInputDescriptor current = null;
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
		} finally {
			this.updating = false;
		}
		if( available.isEmpty() ) {
			this.hint.setText(TuxGuitar.getProperty("practice.input.none"));
		} else if( current == null ) {
			this.inputs.setSelectedValue(available.get(0));
			select(available.get(0));
		} else {
			updateHint(snapshot);
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
		this.panel.layout();
	}
}
