package app.tuxguitar.app.tools.custom.tunernext;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import app.tuxguitar.app.TuxGuitar;
import app.tuxguitar.app.ui.TGApplication;
import app.tuxguitar.app.view.util.TGDialogUtil;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.layout.UITableLayout;
import app.tuxguitar.ui.widget.UIButton;
import app.tuxguitar.ui.widget.UIDropDownSelect;
import app.tuxguitar.ui.widget.UILabel;
import app.tuxguitar.ui.widget.UISelectItem;
import app.tuxguitar.ui.widget.UIWindow;
import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.TGMusicKeyUtils;

/**
 * Custom six-string tuning (FR-053): one target note per string, within TuxGuitar's note range
 * ({@link TunerTuning#MIN_PITCH}–{@link TunerTuning#MAX_PITCH}). Tuner guidance only.
 */
public class TunerCustomDialog {

	private final TGContext context;
	private final UIWindow parent;
	private final TunerTuning initial;
	private final Consumer<TunerTuning> chosen;

	public TunerCustomDialog(TGContext context, UIWindow parent, TunerTuning initial, Consumer<TunerTuning> chosen) {
		this.context = context;
		this.parent = parent;
		this.initial = initial;
		this.chosen = chosen;
	}

	public void open() {
		UIFactory uiFactory = TGApplication.getInstance(this.context).getFactory();
		UITableLayout layout = new UITableLayout();
		final UIWindow dialog = uiFactory.createWindow(this.parent, true, false);
		dialog.setLayout(layout);
		dialog.setText(TuxGuitar.getProperty("tunernext.custom.title"));

		int[] targets = this.initial.getTargets();
		final List<UIDropDownSelect<Integer>> strings = new ArrayList<UIDropDownSelect<Integer>>();
		for( int i = 0 ; i < TunerTuning.STRING_COUNT ; i ++ ) {
			UILabel label = uiFactory.createLabel(dialog);
			label.setText(TuxGuitar.getProperty("tunernext.custom.string", new String[] { Integer.toString(i + 1) }));
			layout.set(label, i + 1, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);
			UIDropDownSelect<Integer> notes = uiFactory.createDropDownSelect(dialog);
			for( int pitch = TunerTuning.MAX_PITCH ; pitch >= TunerTuning.MIN_PITCH ; pitch -- ) {
				notes.addItem(new UISelectItem<Integer>(TGMusicKeyUtils.sharpNoteFullName(pitch), pitch));
			}
			notes.setSelectedValue(targets[i]);
			layout.set(notes, i + 1, 2, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);
			strings.add(notes);
		}

		UIButton ok = uiFactory.createButton(dialog);
		ok.setText(TuxGuitar.getProperty("ok"));
		ok.addSelectionListener(event -> {
			int[] values = new int[TunerTuning.STRING_COUNT];
			for( int i = 0 ; i < values.length ; i ++ ) {
				values[i] = strings.get(i).getSelectedValue();
			}
			dialog.dispose();
			this.chosen.accept(TunerTuning.custom(values));
		});
		layout.set(ok, 7, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false, 1, 1, 80f, 25f, null);
		UIButton cancel = uiFactory.createButton(dialog);
		cancel.setText(TuxGuitar.getProperty("cancel"));
		cancel.addSelectionListener(event -> dialog.dispose());
		layout.set(cancel, 7, 2, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false, 1, 1, 80f, 25f, null);

		TGDialogUtil.openDialog(dialog, TGDialogUtil.OPEN_STYLE_CENTER | TGDialogUtil.OPEN_STYLE_PACK);
	}
}
