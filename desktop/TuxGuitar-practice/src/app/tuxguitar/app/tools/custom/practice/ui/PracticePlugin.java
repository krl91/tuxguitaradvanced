package app.tuxguitar.app.tools.custom.practice.ui;

import java.util.function.Consumer;

import app.tuxguitar.app.TuxGuitar;
import app.tuxguitar.app.action.TGActionAdapterManager;
import app.tuxguitar.app.tools.custom.TGToolItemPlugin;
import app.tuxguitar.app.tools.custom.practice.session.PracticeCommand;
import app.tuxguitar.app.view.dialog.settings.TGSettingsOptionRegistry;
import app.tuxguitar.app.view.toolbar.main.TGMainToolBar;
import app.tuxguitar.app.view.toolbar.main.TGMainToolBarItemRegistry;
import app.tuxguitar.app.view.toolbar.main.TGMainToolBarPluginItem;
import app.tuxguitar.util.TGSynchronizer;
import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.plugin.TGPluginException;

/**
 * Interactive Practice plugin: the Tools menu item and the key-bindable practice actions.
 */
public class PracticePlugin extends TGToolItemPlugin {

	public static final String MODULE_ID = "tuxguitar-practice";

	public static final String OPEN_ACTION_ID = "practice.open";

	/** Name of the transport-bar item (also its tooltip property key) and its place in the default layout. */
	public static final String TOOLBAR_ITEM = "transport.practice";
	private static final String TOOLBAR_ICON = "practice_toolbar.png";
	private static final String TOOLBAR_AFTER = "transport.start";

	private static final String[] ACTION_IDS = {
		PracticeAction.START, PracticeAction.SKIP, PracticeAction.RESUME, PracticeAction.STOP
	};

	private static final PracticeCommand[] COMMANDS = {
		PracticeCommand.START, PracticeCommand.SKIP, PracticeCommand.RESUME, PracticeCommand.STOP
	};

	private volatile Consumer<PracticeCommand> commandSink;
	private PracticeWindow window;

	public String getModuleId() {
		return MODULE_ID;
	}

	protected String getItemName() {
		return OPEN_ACTION_ID;
	}

	protected void doAction(TGContext context) {
		if( this.window == null ) {
			this.window = new PracticeWindow(context, this);
		}
		this.window.open();
	}

	/** Closes the practice window when it is open, else opens it (the transport-bar button). */
	void toggleWindow(TGContext context) {
		if( isWindowOpen() ) {
			this.window.close();
		} else {
			doAction(context);
		}
	}

	boolean isWindowOpen() {
		PracticeWindow current = this.window;
		return (current != null && current.isOpen());
	}

	/** The practice window opened or closed: refreshes the transport-bar button (checked state). */
	void windowStateChanged(TGContext context) {
		TGSynchronizer.getInstance(context).executeLater(new Runnable() {
			public void run() {
				TGMainToolBar.getInstance(context).updateItems();
			}
		});
	}

	public void connect(TGContext context) throws TGPluginException {
		super.connect(context);
		try {
			TGActionAdapterManager adapterManager = TGActionAdapterManager.getInstance(context);
			for( int i = 0 ; i < ACTION_IDS.length ; i ++ ) {
				TuxGuitar.getInstance().getActionManager().mapAction(ACTION_IDS[i], new PracticeAction(context, ACTION_IDS[i], COMMANDS[i], this));
				adapterManager.getKeyBindingActionIds().addActionId(ACTION_IDS[i]);
				adapterManager.getSyncThreadInterceptor().addActionId(ACTION_IDS[i]);
			}
			// the transport-bar Practice button, next to Play; the Tools menu entry stays
			TuxGuitar.getInstance().getActionManager().mapAction(PracticeToggleAction.NAME, new PracticeToggleAction(context, this));
			adapterManager.getKeyBindingActionIds().addActionId(PracticeToggleAction.NAME);
			adapterManager.getSyncThreadInterceptor().addActionId(PracticeToggleAction.NAME);
			TGMainToolBarItemRegistry.getInstance(context).register(new TGMainToolBarPluginItem(TOOLBAR_ITEM, "transport",
					PracticeToggleAction.NAME, TOOLBAR_ICON, true, new PracticeLauncherItem(TOOLBAR_ITEM, this::isWindowOpen), TOOLBAR_AFTER));
			// "Audio recognition" rubric of the Configuration window (T102)
			TGSettingsOptionRegistry.getInstance(context).register(AudioRecognitionOption.factory());
		} catch (Throwable throwable) {
			throw new TGPluginException(throwable.getMessage(), throwable);
		}
	}

	public void disconnect(TGContext context) throws TGPluginException {
		if( this.window != null ) {
			this.window.close();
			this.window = null;
		}
		try {
			TGMainToolBarItemRegistry.getInstance(context).unregister(TOOLBAR_ITEM);
			TuxGuitar.getInstance().getActionManager().unmapAction(PracticeToggleAction.NAME);
			TGActionAdapterManager toolbarAdapter = TGActionAdapterManager.getInstance(context);
			toolbarAdapter.getSyncThreadInterceptor().removeActionId(PracticeToggleAction.NAME);
			toolbarAdapter.getKeyBindingActionIds().removeActionId(PracticeToggleAction.NAME);
			TGSettingsOptionRegistry.getInstance(context).unregister(AudioRecognitionOption.ID);
			TGActionAdapterManager adapterManager = TGActionAdapterManager.getInstance(context);
			for( String actionId : ACTION_IDS ) {
				adapterManager.getSyncThreadInterceptor().removeActionId(actionId);
				adapterManager.getKeyBindingActionIds().removeActionId(actionId);
				TuxGuitar.getInstance().getActionManager().unmapAction(actionId);
			}
		} catch (Throwable throwable) {
			throw new TGPluginException(throwable.getMessage(), throwable);
		}
		super.disconnect(context);
	}

	/** Where the practice actions post their commands; null while no session listens. */
	public Consumer<PracticeCommand> getCommandSink() {
		return this.commandSink;
	}

	public void setCommandSink(Consumer<PracticeCommand> commandSink) {
		this.commandSink = commandSink;
	}
}
