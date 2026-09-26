package app.tuxguitar.app.tools.custom.practice.ui;

import java.util.function.Consumer;

import app.tuxguitar.action.TGActionContext;
import app.tuxguitar.app.tools.custom.practice.session.PracticeCommand;
import app.tuxguitar.editor.action.TGActionBase;
import app.tuxguitar.util.TGContext;

/**
 * Key-bindable practice action. It only posts its command to the current command sink
 * (the practice session); it never changes session state or the song itself.
 */
public class PracticeAction extends TGActionBase {

	public static final String START = "action.practice.start";
	public static final String SKIP = "action.practice.skip";
	public static final String RESUME = "action.practice.resume";
	public static final String STOP = "action.practice.stop";

	private final PracticeCommand command;
	private final PracticePlugin plugin;

	public PracticeAction(TGContext context, String name, PracticeCommand command, PracticePlugin plugin) {
		super(context, name);
		this.command = command;
		this.plugin = plugin;
	}

	protected void processAction(TGActionContext context) {
		Consumer<PracticeCommand> sink = this.plugin.getCommandSink();
		if( sink != null ) {
			sink.accept(this.command);
		}
	}
}
