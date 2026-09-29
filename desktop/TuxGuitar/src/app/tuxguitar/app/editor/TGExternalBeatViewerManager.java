package app.tuxguitar.app.editor;

import java.util.List;

import app.tuxguitar.editor.TGEditorManager;
import app.tuxguitar.event.TGEventListener;
import app.tuxguitar.event.TGEventManager;
import app.tuxguitar.song.models.TGBeat;
import app.tuxguitar.song.models.TGNote;
import app.tuxguitar.util.TGAbstractContext;
import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.singleton.TGSingletonFactory;
import app.tuxguitar.util.singleton.TGSingletonUtil;

public class TGExternalBeatViewerManager {

	private TGContext context;

	public TGExternalBeatViewerManager(TGContext context){
		this.context = context;
	}

	public void showExternalBeat(final TGBeat beat){
		this.showExternalBeat(beat, null);
	}

	public void showExternalBeat(final TGBeat beat, final TGAbstractContext context){
		this.asyncRunLocked(new Runnable() {
			public void run() {
				doShowExternalBeat(beat, context);
			}
		});
	}

	/**
	 * Shows the beat and frames the given notes in the score and tablature (expected), plus
	 * optional other notes framed as accepted; views that only know beats show the beat.
	 */
	public void showExternalNotes(final TGBeat beat, final List<TGNote> notes, final List<TGNote> acceptedNotes){
		this.asyncRunLocked(new Runnable() {
			public void run() {
				TGEventManager.getInstance(TGExternalBeatViewerManager.this.context).fireEvent(
						new TGExternalBeatViewerEvent(TGExternalBeatViewerEvent.ACTION_SHOW, beat, notes, acceptedNotes, null));
			}
		});
	}

	public void hideExternalBeat(){
		this.hideExternalBeat(null);
	}

	public void hideExternalBeat(final TGAbstractContext context){
		this.asyncRunLocked(new Runnable() {
			public void run() {
				doHideExternalBeat(context);
			}
		});
	}

	public void addBeatViewerListener(TGEventListener listener){
		TGEventManager.getInstance(this.context).addListener(TGExternalBeatViewerEvent.EVENT_TYPE, listener);
	}

	public void removeBeatViewerListener(TGEventListener listener){
		TGEventManager.getInstance(this.context).removeListener(TGExternalBeatViewerEvent.EVENT_TYPE, listener);
	}

	private void doShowExternalBeat(TGBeat beat, TGAbstractContext context){
		TGEventManager.getInstance(this.context).fireEvent(new TGExternalBeatViewerEvent(TGExternalBeatViewerEvent.ACTION_SHOW, beat, context));
	}

	private void doHideExternalBeat(TGAbstractContext context){
		TGEventManager.getInstance(this.context).fireEvent(new TGExternalBeatViewerEvent(TGExternalBeatViewerEvent.ACTION_HIDE, context));
	}

	public void asyncRunLocked(final Runnable runnable) {
		TGEditorManager.getInstance(this.context).asyncRunLocked(runnable);
	}

	public static TGExternalBeatViewerManager getInstance(TGContext context) {
		return TGSingletonUtil.getInstance(context, TGExternalBeatViewerManager.class.getName(), new TGSingletonFactory<TGExternalBeatViewerManager>() {
			public TGExternalBeatViewerManager createInstance(TGContext context) {
				return new TGExternalBeatViewerManager(context);
			}
		});
	}
}