package app.tuxguitar.app.editor;

import java.util.List;

import app.tuxguitar.event.TGEvent;
import app.tuxguitar.song.models.TGBeat;
import app.tuxguitar.song.models.TGNote;
import app.tuxguitar.util.TGAbstractContext;

public class TGExternalBeatViewerEvent extends TGEvent {

	public static final String EVENT_TYPE = "ui-external-beat-viewer";
	public static final String PROPERTY_ACTION = "action";
	public static final String PROPERTY_BEAT = "beat";
	public static final String PROPERTY_NOTES = "notes";
	public static final String PROPERTY_ACCEPTED_NOTES = "acceptedNotes";

	public static final String ACTION_SHOW = "show";
	public static final String ACTION_HIDE = "hide";

	public TGExternalBeatViewerEvent(String action, TGAbstractContext context) {
		super(EVENT_TYPE, context);

		this.setAttribute(PROPERTY_ACTION, action);
	}

	public TGExternalBeatViewerEvent(String action, TGBeat beat, TGAbstractContext context) {
		this(action, context);

		this.setAttribute(PROPERTY_BEAT, beat);
	}

	/** Shows the beat with its given notes framed (expected) and, optionally, notes framed as accepted. */
	public TGExternalBeatViewerEvent(String action, TGBeat beat, List<TGNote> notes, List<TGNote> acceptedNotes, TGAbstractContext context) {
		this(action, beat, context);

		this.setAttribute(PROPERTY_NOTES, notes);
		this.setAttribute(PROPERTY_ACCEPTED_NOTES, acceptedNotes);
	}
}
