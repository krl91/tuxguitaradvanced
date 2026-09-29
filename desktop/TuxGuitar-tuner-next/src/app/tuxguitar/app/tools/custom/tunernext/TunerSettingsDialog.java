package app.tuxguitar.app.tools.custom.tunernext;

import java.util.List;

import app.tuxguitar.app.TuxGuitar;
import app.tuxguitar.app.ui.TGApplication;
import app.tuxguitar.app.view.util.TGDialogUtil;
import app.tuxguitar.audio.engine.AudioEngine;
import app.tuxguitar.audio.engine.input.AudioInputDescriptor;
import app.tuxguitar.audio.engine.input.CurrentInputSelection;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.layout.UITableLayout;
import app.tuxguitar.ui.widget.UIButton;
import app.tuxguitar.ui.widget.UIDropDownSelect;
import app.tuxguitar.ui.widget.UILabel;
import app.tuxguitar.ui.widget.UISelectItem;
import app.tuxguitar.ui.widget.UIWindow;
import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.TGSynchronizer;

/**
 * Tuner settings (FR-059): the shared input/channel (FR-035, FR-048), the level-check result
 * (FR-037, FR-038) and an explicit level check (FR-039) — nothing else. The legacy detector
 * controls (FFT and buffer sizes, sample rate and size, noise gate) are gone and their names are
 * not reused. Changing the input here changes it for practice too.
 */
public class TunerSettingsDialog {

	private final TGContext context;
	private final UIWindow parent;
	private final AudioEngine engine;

	public TunerSettingsDialog(TGContext context, UIWindow parent, AudioEngine engine) {
		this.context = context;
		this.parent = parent;
		this.engine = engine;
	}

	public void open() {
		UIFactory uiFactory = TGApplication.getInstance(this.context).getFactory();
		UITableLayout layout = new UITableLayout();
		final UIWindow dialog = uiFactory.createWindow(this.parent, true, false);
		dialog.setLayout(layout);
		dialog.setText(TuxGuitar.getProperty("tunernext.settings"));

		UILabel inputLabel = uiFactory.createLabel(dialog);
		inputLabel.setText(TuxGuitar.getProperty("tunernext.settings.input"));
		layout.set(inputLabel, 1, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);
		final UIDropDownSelect<AudioInputDescriptor> inputs = uiFactory.createDropDownSelect(dialog);
		layout.set(inputs, 1, 2, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);
		CurrentInputSelection.Snapshot snapshot = this.engine.getSelection().snapshot();
		List<AudioInputDescriptor> available = this.engine.getCatalog().listInputs();
		for( AudioInputDescriptor input : available ) {
			inputs.addItem(new UISelectItem<AudioInputDescriptor>(input.getDisplayName(), input));
			if( snapshot.getInput() != null && input.getId().equals(snapshot.getInput().getId()) ) {
				inputs.setSelectedValue(input);
			}
		}
		inputs.addSelectionListener(event -> {
			AudioInputDescriptor input = inputs.getSelectedValue();
			if( input != null ) {
				this.engine.getSelection().select(input, null);
			}
		});

		final UILabel status = uiFactory.createLabel(dialog);
		layout.set(status, 2, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false, 1, 2);
		showAssessment(status, snapshot.getAssessment());
		final CurrentInputSelection.Listener listener = changed -> TGSynchronizer.getInstance(this.context).executeLater(() -> {
			if( !status.isDisposed() ) {
				showAssessment(status, changed.getAssessment());
				dialog.layout();
			}
		});
		this.engine.getSelection().addListener(listener);
		dialog.addDisposeListener(event -> this.engine.getSelection().removeListener(listener));

		UIButton check = uiFactory.createButton(dialog);
		check.setText(TuxGuitar.getProperty("tunernext.settings.check-level"));
		check.addSelectionListener(event -> this.engine.runLevelCheck());
		layout.set(check, 3, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false, 1, 1, 80f, 25f, null);
		UIButton close = uiFactory.createButton(dialog);
		close.setText(TuxGuitar.getProperty("close"));
		close.addSelectionListener(event -> dialog.dispose());
		layout.set(close, 3, 2, UITableLayout.ALIGN_RIGHT, UITableLayout.ALIGN_CENTER, false, false, 1, 1, 80f, 25f, null);

		TGDialogUtil.openDialog(dialog, TGDialogUtil.OPEN_STYLE_CENTER | TGDialogUtil.OPEN_STYLE_PACK);
	}

	private static void showAssessment(UILabel label, InputAssessment assessment) {
		String reason = TuxGuitar.getProperty("tunernext.input.reason." + assessment.getReason().name());
		label.setText(TuxGuitar.getProperty("tunernext.input.status." + assessment.getStatus().name(), new String[] { reason }));
	}
}
