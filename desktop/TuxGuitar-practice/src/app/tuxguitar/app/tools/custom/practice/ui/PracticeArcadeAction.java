package app.tuxguitar.app.tools.custom.practice.ui;

import app.tuxguitar.action.TGActionContext;
import app.tuxguitar.editor.action.TGActionBase;
import app.tuxguitar.util.TGContext;

/** The action of the coloured Arcade button of the transport bar: Arcade starts now on the track under the cursor. */
public class PracticeArcadeAction extends TGActionBase {

	public static final String NAME = "practice.arcade";

	private final PracticePlugin plugin;

	public PracticeArcadeAction(TGContext context, PracticePlugin plugin) {
		super(context, NAME);
		this.plugin = plugin;
	}

	protected void processAction(TGActionContext context) {
		this.plugin.startArcadeNow(getContext());
	}
}
