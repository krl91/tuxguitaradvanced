package app.tuxguitar.app.tools.custom.practice.ui;

import java.util.function.Consumer;

import app.tuxguitar.app.TuxGuitar;
import app.tuxguitar.app.action.TGActionAdapterManager;
import app.tuxguitar.app.tools.custom.TGToolItemPlugin;
import app.tuxguitar.app.tools.custom.practice.session.PracticeCommand;
import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.plugin.TGPluginException;

/**
 * Interactive Practice plugin: the Tools menu item and the key-bindable practice actions.
 */
public class PracticePlugin extends TGToolItemPlugin {

	public static final String MODULE_ID = "tuxguitar-practice";

	public static final String OPEN_ACTION_ID = "practice.open";

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

	public void connect(TGContext context) throws TGPluginException {
		super.connect(context);
		try {
			TGActionAdapterManager adapterManager = TGActionAdapterManager.getInstance(context);
			for( int i = 0 ; i < ACTION_IDS.length ; i ++ ) {
				TuxGuitar.getInstance().getActionManager().mapAction(ACTION_IDS[i], new PracticeAction(context, ACTION_IDS[i], COMMANDS[i], this));
				adapterManager.getKeyBindingActionIds().addActionId(ACTION_IDS[i]);
				adapterManager.getSyncThreadInterceptor().addActionId(ACTION_IDS[i]);
			}
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
