package app.tuxguitar.app.action.listener.cache.controller;

import app.tuxguitar.action.TGActionContext;
import app.tuxguitar.app.action.impl.edit.TGOpenMeasureErrorsDialogAction;
import app.tuxguitar.document.TGDocumentManager;
import app.tuxguitar.editor.action.TGActionProcessor;
import app.tuxguitar.util.TGContext;

public class TGUpdateReadSongController extends TGUpdateItemsController {
	
	public TGUpdateReadSongController() {
		super();
	}
	
	@Override
	public void update(TGContext context, TGActionContext actionContext) {
		TGDocumentManager tgDocumentManager = TGDocumentManager.getInstance(context);
		// A file that was read is opened silently, as Guitar Pro does: measure problems are listed on
		// demand by the Edit > "Display invalid measures" dialog (TGOpenMeasureErrorsDialogAction).
		// The dialog opens by itself only when a problem is blocking (see hasBlockingError).
		if (hasBlockingError(tgDocumentManager)) {
			TGActionProcessor tgActionProcessor = new TGActionProcessor(context, TGOpenMeasureErrorsDialogAction.NAME);
			tgActionProcessor.process();
		}
	}

	/**
	 * Measure-duration and tied-note problems never prevent the song from being read, shown or played
	 * (the learner may still want to fix them), so none is blocking for now. A read failure is reported
	 * by the reader itself, before this controller runs.
	 */
	protected boolean hasBlockingError(TGDocumentManager tgDocumentManager) {
		return false;
	}

}
