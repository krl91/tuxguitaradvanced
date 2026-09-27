package app.tuxguitar.app.tools.custom.practice.ui;

import app.tuxguitar.app.editor.TGExternalBeatViewerManager;
import app.tuxguitar.song.models.TGBeat;
import app.tuxguitar.util.TGContext;

/**
 * The practice highlight in the score (FR-014): shown through TuxGuitar's external beat viewer
 * API, separate from the normal cursor, which it never moves. Hidden whenever the session stops.
 */
public class PracticeHighlight {

	private final TGContext context;
	private TGBeat shownBeat;

	public PracticeHighlight(TGContext context) {
		this.context = context;
	}

	/** Shows the beat of the current practice event; does nothing if it is already shown. */
	public void show(TGBeat beat) {
		if( beat == null ) {
			hide();
		} else if( beat != this.shownBeat ) {
			this.shownBeat = beat;
			TGExternalBeatViewerManager.getInstance(this.context).showExternalBeat(beat);
		}
	}

	public void hide() {
		if( this.shownBeat != null ) {
			this.shownBeat = null;
			TGExternalBeatViewerManager.getInstance(this.context).hideExternalBeat();
		}
	}
}
