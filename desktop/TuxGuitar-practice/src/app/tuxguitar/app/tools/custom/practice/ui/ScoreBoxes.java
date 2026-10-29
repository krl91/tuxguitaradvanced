package app.tuxguitar.app.tools.custom.practice.ui;

import app.tuxguitar.app.tools.custom.practice.model.PracticeEvent;
import app.tuxguitar.app.tools.custom.practice.ui.PaletteDodge.Box;
import app.tuxguitar.app.view.component.tab.TGControl;
import app.tuxguitar.app.view.component.tab.TGExternalNoteFramePainter;
import app.tuxguitar.app.view.component.tab.Tablature;
import app.tuxguitar.app.view.component.tab.TablatureEditor;
import app.tuxguitar.app.view.component.tabfolder.TGTabFolder;
import app.tuxguitar.ui.resource.UIPosition;
import app.tuxguitar.ui.resource.UIRectangle;
import app.tuxguitar.ui.widget.UICanvas;
import app.tuxguitar.util.TGContext;

/**
 * The screen boxes the palette must keep clear (spec 017, research R2 and R3): the score area (the canvas of the selected score, mapped to the screen)
 * and the box of an event's notes, as the frame painter draws it. Null whenever something is unknown: no score, a toolkit without screen mapping, notes
 * out of the visible score. Reads only; UI thread only.
 */
final class ScoreBoxes {

	private final TGContext context;

	ScoreBoxes(TGContext context) {
		this.context = context;
	}

	/** The score area on the screen, or null. */
	Box area() {
		UICanvas canvas = canvas();
		UIPosition origin = (canvas != null ? canvas.toDisplay(new UIPosition(0f, 0f)) : null);
		if( origin == null ) {
			return null;
		}
		UIRectangle bounds = canvas.getBounds();
		return new Box(origin.getX(), origin.getY(), bounds.getWidth(), bounds.getHeight());
	}

	/** The box of the event's notes on the screen, or null when it is not on the visible score. */
	Box of(PracticeEvent event, Box area) {
		if( event == null || area == null ) {
			return null;
		}
		Tablature tablature = TablatureEditor.getInstance(this.context).getTablature();
		UIRectangle frame = TGExternalNoteFramePainter.frameBounds(tablature.getViewLayout(), event.getNotes(), tablature.getTrackSelection());
		if( frame == null ) {
			return null;
		}
		Box box = new Box(area.x() + frame.getX(), area.y() + frame.getY(), frame.getWidth(), frame.getHeight());
		return box.overlap(area) > 0.0 ? box : null;
	}

	private UICanvas canvas() {
		TGControl control = TGTabFolder.getInstance(this.context).findSelectedControl();
		return (control != null && !control.isDisposed() ? control.getCanvas() : null);
	}
}
