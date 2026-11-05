package app.tuxguitar.app.tools.custom.practice.ui;

import java.util.concurrent.Executor;

import app.tuxguitar.app.TuxGuitar;
import app.tuxguitar.audio.engine.settings.ChordLeniency;
import app.tuxguitar.audio.engine.settings.ChordLeniency.Level;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.audio.engine.settings.RecognitionSettingsStore;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.event.UISelectionEvent;
import app.tuxguitar.ui.event.UISelectionListener;
import app.tuxguitar.ui.layout.UITableLayout;
import app.tuxguitar.ui.widget.UIContainer;
import app.tuxguitar.ui.widget.UIDropDownSelect;
import app.tuxguitar.ui.widget.UILabel;
import app.tuxguitar.ui.widget.UIPanel;
import app.tuxguitar.ui.widget.UISelectItem;

/**
 * The chord check setting of the <i>Mode</i> tab (spec 024, contracts/chord-leniency.md, FR-005 to FR-008): <i>Strict</i>, <i>Normal</i> or
 * <i>Lenient</i>, with a line saying what the level accepts and risks. The level is not stored: it is derived from the default layer of the
 * recognition settings, and choosing one writes its values there at once ({@link ChordLeniency#apply}). While the settings match no level,
 * <i>Custom</i> is shown at the end of the list; it cannot be chosen. The control stays enabled during a run: the next run uses the new values.
 */
public final class ChordLeniencyPanel {

	private static final String KEY = "practice.chord-leniency";

	private final RecognitionSettingsStore store;
	private final UIPanel panel;
	private final UILabel label;
	private final UIDropDownSelect<Level> select;
	private final UILabel line;
	private final UISelectItem<Level> custom;
	private final RecognitionSettingsStore.Listener settingsListener;
	private Runnable contentListener;
	private boolean customShown;
	private boolean updating;
	private boolean disposed;

	/** @param uiThread runs the refresh on the UI thread, as the store may tell a change from another thread */
	public ChordLeniencyPanel(UIFactory uiFactory, UIContainer parent, RecognitionSettingsStore store, Executor uiThread) {
		this.store = store;
		this.panel = uiFactory.createPanel(parent, false);
		UITableLayout layout = new UITableLayout();
		this.panel.setLayout(layout);

		this.label = uiFactory.createLabel(this.panel);
		this.label.setText(TuxGuitar.getProperty(KEY));
		layout.set(this.label, 1, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);
		this.select = uiFactory.createDropDownSelect(this.panel);
		for( Level level : ChordLeniency.NAMED ) {
			this.select.addItem(new UISelectItem<Level>(text(level), level));
		}
		this.custom = new UISelectItem<Level>(text(Level.CUSTOM), Level.CUSTOM);
		layout.set(this.select, 1, 2, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);
		this.line = uiFactory.createWrapLabel(this.panel);
		layout.set(this.line, 2, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false, 1, 2);
		layout.set(this.line, UITableLayout.PACKED_WIDTH, InputSelectorPanel.WRAP_WIDTH);

		this.select.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				if( !ChordLeniencyPanel.this.updating ) {
					chosen(ChordLeniencyPanel.this.select.getSelectedValue());
				}
			}
		});
		this.settingsListener = (previous, current) -> uiThread.execute(() -> {
			if( !this.disposed ) {
				refresh();
			}
		});
		this.store.addListener(this.settingsListener);
		refresh();
	}

	private static String text(Level level) {
		return TuxGuitar.getProperty(KEY + "." + level.name().toLowerCase());
	}

	/** Called after the line changed length, so the window can refit (UI thread). */
	public void setContentListener(Runnable contentListener) {
		this.contentListener = contentListener;
	}

	public UIPanel getControl() {
		return this.panel;
	}

	UILabel label() {
		return this.label;
	}

	UIDropDownSelect<Level> levelSelect() {
		return this.select;
	}

	UILabel line() {
		return this.line;
	}

	/** Releases the store listener; the window calls it when it closes. */
	public void dispose() {
		this.disposed = true;
		this.store.removeListener(this.settingsListener);
	}

	private void chosen(Level level) {
		if( level == null || level == Level.CUSTOM ) {
			// Custom cannot be chosen: show again what the settings hold
			refresh();
			return;
		}
		RecognitionSettings current = this.store.getDefault();
		if( ChordLeniency.of(current) != level ) {
			this.store.update(ChordLeniency.apply(level, current));
		}
		refresh();
	}

	/** Shows the level the default layer holds, with Custom listed only while it holds none. */
	private void refresh() {
		if( this.panel.isDisposed() ) {
			return;
		}
		Level level = ChordLeniency.of(this.store.getDefault());
		this.updating = true;
		try {
			if( level == Level.CUSTOM && !this.customShown ) {
				this.select.addItem(this.custom);
				this.customShown = true;
			} else if( level != Level.CUSTOM && this.customShown ) {
				this.select.removeItem(this.custom);
				this.customShown = false;
			}
			this.select.setSelectedValue(level);
		} finally {
			this.updating = false;
		}
		String text = TuxGuitar.getProperty(KEY + "." + level.name().toLowerCase() + ".line");
		if( !text.equals(this.line.getText()) ) {
			this.line.setText(text);
			Runnable listener = this.contentListener;
			if( listener != null ) {
				listener.run();
			}
		}
	}
}
