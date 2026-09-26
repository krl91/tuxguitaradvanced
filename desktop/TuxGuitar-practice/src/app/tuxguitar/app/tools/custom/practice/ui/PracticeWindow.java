package app.tuxguitar.app.tools.custom.practice.ui;

import app.tuxguitar.app.TuxGuitar;
import app.tuxguitar.app.system.keybindings.KeyBindingActionManager;
import app.tuxguitar.app.ui.TGApplication;
import app.tuxguitar.app.view.main.TGWindow;
import app.tuxguitar.app.view.util.TGDialogUtil;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.event.UIDisposeEvent;
import app.tuxguitar.ui.event.UIDisposeListener;
import app.tuxguitar.ui.event.UISelectionEvent;
import app.tuxguitar.ui.event.UISelectionListener;
import app.tuxguitar.ui.layout.UITableLayout;
import app.tuxguitar.ui.widget.UIButton;
import app.tuxguitar.ui.widget.UIPanel;
import app.tuxguitar.ui.widget.UIWindow;
import app.tuxguitar.util.TGContext;

/**
 * Non-modal Interactive Practice window. Shell only: the preparation and running views are
 * built inside {@link #getContent()} by later tasks. Practice shortcuts work while it has focus.
 */
public class PracticeWindow {

	private final TGContext context;
	private UIWindow window;
	private UIPanel content;

	public PracticeWindow(TGContext context) {
		this.context = context;
	}

	/** Opens the window, or brings it to the front when it is already open. */
	public void open() {
		if( this.isOpen() ) {
			this.window.moveToTop();
			return;
		}
		UIFactory uiFactory = TGApplication.getInstance(this.context).getFactory();
		UIWindow uiParent = TGWindow.getInstance(this.context).getWindow();
		UITableLayout windowLayout = new UITableLayout();

		this.window = uiFactory.createWindow(uiParent, false, true);
		this.window.setLayout(windowLayout);
		this.window.setImage(TuxGuitar.getInstance().getIconManager().getAppIcon());
		this.window.setText(TuxGuitar.getProperty(PracticePlugin.OPEN_ACTION_ID));

		this.content = uiFactory.createPanel(this.window, false);
		this.content.setLayout(new UITableLayout());
		windowLayout.set(this.content, 1, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_FILL, true, true, 1, 1, 480f, 320f, null);

		UITableLayout buttonsLayout = new UITableLayout(0f);
		UIPanel buttons = uiFactory.createPanel(this.window, false);
		buttons.setLayout(buttonsLayout);
		windowLayout.set(buttons, 2, 1, UITableLayout.ALIGN_RIGHT, UITableLayout.ALIGN_FILL, true, false);

		UIButton closeButton = uiFactory.createButton(buttons);
		closeButton.setText(TuxGuitar.getProperty("close"));
		closeButton.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				PracticeWindow.this.close();
			}
		});
		buttonsLayout.set(closeButton, 1, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_FILL, true, true, 1, 1, 80f, 25f, null);
		buttonsLayout.set(closeButton, UITableLayout.MARGIN_RIGHT, 0f);

		KeyBindingActionManager keyBindings = KeyBindingActionManager.getInstance(this.context);
		keyBindings.appendListenersTo(this.content);
		keyBindings.appendListenersTo(buttons);
		keyBindings.appendListenersTo(closeButton);

		this.window.addDisposeListener(new UIDisposeListener() {
			public void onDispose(UIDisposeEvent event) {
				PracticeWindow.this.window = null;
				PracticeWindow.this.content = null;
			}
		});

		TGDialogUtil.openDialog(this.window, TGDialogUtil.OPEN_STYLE_CENTER | TGDialogUtil.OPEN_STYLE_PACK);
	}

	public void close() {
		if( this.isOpen() ) {
			this.window.dispose();
		}
	}

	public boolean isOpen() {
		return (this.window != null && !this.window.isDisposed());
	}

	/** Container for the preparation and running views; null while the window is closed. */
	public UIPanel getContent() {
		return this.content;
	}
}
