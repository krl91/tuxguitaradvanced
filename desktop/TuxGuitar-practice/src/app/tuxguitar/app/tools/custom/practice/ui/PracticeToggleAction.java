package app.tuxguitar.app.tools.custom.practice.ui;

import app.tuxguitar.action.TGActionContext;
import app.tuxguitar.editor.action.TGActionBase;
import app.tuxguitar.util.TGContext;

/**
 * The action of the transport-bar Practice button: opens the practice window, or closes it when it
 * is already open (so the button, which shows a checked state, behaves as a toggle). The Tools menu
 * entry keeps its own action, which only opens or brings the window forward.
 */
public class PracticeToggleAction extends TGActionBase {

	public static final String NAME = "practice.toggle";

	private final PracticePlugin plugin;

	public PracticeToggleAction(TGContext context, PracticePlugin plugin) {
		super(context, NAME);
		this.plugin = plugin;
	}

	protected void processAction(TGActionContext context) {
		this.plugin.toggleWindow(getContext());
	}
}
