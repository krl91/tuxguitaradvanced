package app.tuxguitar.app.tools.custom.practice.ui;

import app.tuxguitar.app.TuxGuitar;
import app.tuxguitar.app.view.dialog.settings.TGSettingsEditor;
import app.tuxguitar.app.view.dialog.settings.TGSettingsOptionFactory;
import app.tuxguitar.app.view.dialog.settings.items.TGSettingsOption;
import app.tuxguitar.audio.engine.AudioEngine;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.audio.engine.settings.RecognitionSettingsStore;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.toolbar.UIToolBar;
import app.tuxguitar.ui.widget.UIControl;
import app.tuxguitar.ui.widget.UILayoutContainer;
import app.tuxguitar.ui.widget.UIPanel;
import app.tuxguitar.util.TGContext;

/**
 * "Audio recognition" page of TuxGuitar's Configuration window (task T102). The widgets are in
 * {@link RecognitionSettingsPanel}; this class connects them to the window's contract:
 * <ul>
 * <li>OK ({@link #updateConfig}) saves the page's pending values;</li>
 * <li>Cancel saves nothing;</li>
 * <li>the page's two restore buttons only change the pending values of one category, or of the
 * whole page — they take effect with OK;</li>
 * <li>TuxGuitar's own "Defaults" button ({@link #updateDefaults}) closes the window and restores
 * every TuxGuitar setting at once, audio recognition included.</li>
 * </ul>
 */
public final class AudioRecognitionOption extends TGSettingsOption {

	public static final String ID = "practice.audio-recognition";

	private final TGContext context;
	private final RecognitionSettingsStore store;
	private RecognitionSettingsPanel panel;

	public AudioRecognitionOption(TGSettingsEditor editor, UIToolBar toolBar, UILayoutContainer parent) {
		super(editor, toolBar, parent, TuxGuitar.getProperty("recognition.title"));
		this.context = editor.getViewContext().getContext();
		this.store = RecognitionSettingsStore.getInstance(this.context);
	}

	/** The factory registered by the practice plugin in the Configuration window's extension point. */
	public static TGSettingsOptionFactory factory() {
		return new TGSettingsOptionFactory() {
			public String getId() {
				return ID;
			}

			public TGSettingsOption createOption(TGSettingsEditor editor, UIToolBar toolBar, UILayoutContainer parent) {
				return new AudioRecognitionOption(editor, toolBar, parent);
			}
		};
	}

	/** Translated texts of the application. */
	static final RecognitionSettingsPanel.Messages MESSAGES = new RecognitionSettingsPanel.Messages() {
		public String get(String key) {
			return TuxGuitar.getProperty(key);
		}

		public String get(String key, String[] arguments) {
			return TuxGuitar.getProperty(key, arguments);
		}
	};

	@Override
	public void createOption() {
		getToolItem().setText(TuxGuitar.getProperty("recognition.title"));
		getToolItem().setImage(TuxGuitar.getInstance().getIconManager().getOptionSound());
		getToolItem().addSelectionListener(this);

		this.panel = new RecognitionSettingsPanel(getUIFactory(), getPanel(), MESSAGES, this.store.get(),
				new RecognitionSettingsPanel.InputSection() {
					private InputSelectorPanel selector;

					public UIControl create(UIFactory factory, UIPanel parent) {
						// the shared per-run input (device/channel) of the tuner and practice
						this.selector = new InputSelectorPanel(AudioRecognitionOption.this.context,
								AudioEngine.getInstance(AudioRecognitionOption.this.context), factory, parent);
						return this.selector.getControl();
					}

					public void dispose() {
						if( this.selector != null ) {
							this.selector.dispose();
							this.selector = null;
						}
					}
				},
				() -> {
					if( !isDisposed() ) {
						pack();
					}
				});
		this.panel.build();
	}

	@Override
	public void setVisible(boolean visible) {
		super.setVisible(visible);
		if( visible && this.panel != null ) {
			this.panel.showSection(this.panel.getSelectedSection());
		}
	}

	/** OK: the page's pending values are saved and applied. */
	@Override
	public void updateConfig() {
		commit(this.store, this.panel.getPending());
	}

	/** TuxGuitar's "Defaults" button: every audio-recognition value back to its recommended value, at once. */
	@Override
	public void updateDefaults() {
		applyTuxGuitarDefaults(this.store);
	}

	static void commit(RecognitionSettingsStore store, RecognitionSettings pending) {
		store.update(pending);
	}

	static void applyTuxGuitarDefaults(RecognitionSettingsStore store) {
		store.restoreDefaults();
	}

	@Override
	public void dispose() {
		if( this.panel != null ) {
			this.panel.dispose();
			this.panel = null;
		}
	}
}
