package app.tuxguitar.app.view.component.tab;

import java.util.List;

import app.tuxguitar.graphics.control.TGBeatImpl;
import app.tuxguitar.graphics.control.TGLayout;
import app.tuxguitar.graphics.control.TGMeasureImpl;
import app.tuxguitar.graphics.control.TGNoteImpl;
import app.tuxguitar.graphics.control.TGTrackSpacing;
import app.tuxguitar.song.models.TGNote;
import app.tuxguitar.ui.resource.UIColor;
import app.tuxguitar.ui.resource.UIColorModel;
import app.tuxguitar.ui.resource.UIPainter;
import app.tuxguitar.ui.resource.UIResourceFactory;

/**
 * Frames externally shown notes (e.g. the expected practice note or chord) directly in the
 * score and the tablature: one frame per displayed staff around all given notes of a measure.
 * Expected notes are framed red, accepted notes green. Paints only; never changes the song.
 */
public class TGExternalNoteFramePainter {

	private static final UIColorModel EXPECTED_COLOR = new UIColorModel(220, 0, 0);
	private static final UIColorModel ACCEPTED_COLOR = new UIColorModel(0, 160, 0);

	private UIColor expectedColor;
	private UIColor acceptedColor;

	public void paint(TGLayout layout, UIPainter painter, UIResourceFactory factory, List<TGNote> notes, boolean accepted, int trackSelection) {
		if( notes == null || notes.isEmpty() ) {
			return;
		}
		// notes of one event share a beat, hence a measure
		if( !(notes.get(0) instanceof TGNoteImpl) ) {
			return;
		}
		TGMeasureImpl measure = ((TGNoteImpl) notes.get(0)).getMeasureImpl();
		if( measure == null || measure.isOutOfBounds() || (trackSelection >= 0 && measure.getTrack().getNumber() != trackSelection) ) {
			return;
		}
		painter.setForeground(getColor(factory, accepted));
		painter.setLineWidth(Math.max(1f, 1.5f * layout.getScale()));
		painter.setAntialias(true);
		if( (layout.getStyle() & TGLayout.DISPLAY_SCORE) != 0 ) {
			paintScoreFrame(layout, painter, measure, notes);
		}
		if( (layout.getStyle() & TGLayout.DISPLAY_TABLATURE) != 0 ) {
			paintTablatureFrame(layout, painter, measure, notes);
		}
	}

	private void paintScoreFrame(TGLayout layout, UIPainter painter, TGMeasureImpl measure, List<TGNote> notes) {
		float scale = layout.getScale();
		float line = layout.getScoreLineSpacing();
		float fromX = measure.getPosX() + measure.getHeaderImpl().getLeftSpacing(layout);
		float fromY = measure.getPosY() + measure.getTs().getPosition(TGTrackSpacing.POSITION_SCORE_MIDDLE_LINES);
		float x1 = Float.MAX_VALUE, y1 = Float.MAX_VALUE, x2 = -Float.MAX_VALUE, y2 = -Float.MAX_VALUE;
		for( TGNote note : notes ) {
			TGNoteImpl impl = (TGNoteImpl) note;
			TGBeatImpl beat = impl.getBeatImpl();
			// top-left corner of the note head, as painted by TGNoteImpl.paintScoreNote; the left margin covers an accidental
			float x = fromX + impl.getPosX() + beat.getSpacing(layout);
			float y = fromY + impl.getScorePosY();
			x1 = Math.min(x1, x - line);
			x2 = Math.max(x2, x + layout.getScoreNoteWidth() + (3f * scale));
			y1 = Math.min(y1, y - (3f * scale));
			y2 = Math.max(y2, y + line + (3f * scale));
		}
		paintFrame(painter, x1, y1, x2, y2, scale);
	}

	private void paintTablatureFrame(TGLayout layout, UIPainter painter, TGMeasureImpl measure, List<TGNote> notes) {
		float scale = layout.getScale();
		float stringSpacing = layout.getStringSpacing();
		float fromX = measure.getPosX() + measure.getHeaderImpl().getLeftSpacing(layout) + (2f * scale);
		float fromY = measure.getPosY() + measure.getTs().getPosition(TGTrackSpacing.POSITION_TABLATURE);
		float x1 = Float.MAX_VALUE, y1 = Float.MAX_VALUE, x2 = -Float.MAX_VALUE, y2 = -Float.MAX_VALUE;
		for( TGNote note : notes ) {
			TGNoteImpl impl = (TGNoteImpl) note;
			TGBeatImpl beat = impl.getBeatImpl();
			// centre of the fret number, as painted by TGNoteImpl.paintTablatureNote
			float x = fromX + impl.getPosX() + beat.getSpacing(layout);
			float y = fromY + impl.getTabPosY();
			float halfWidth = (stringSpacing * (note.getValue() >= 10 ? 0.75f : 0.5f)) + (2f * scale);
			float halfHeight = (stringSpacing / 2f);
			x1 = Math.min(x1, x - halfWidth);
			x2 = Math.max(x2, x + halfWidth);
			y1 = Math.min(y1, y - halfHeight);
			y2 = Math.max(y2, y + halfHeight);
		}
		paintFrame(painter, x1, y1, x2, y2, scale);
	}

	private void paintFrame(UIPainter painter, float x1, float y1, float x2, float y2, float scale) {
		painter.initPath(UIPainter.PATH_DRAW);
		painter.addRoundedRectangle(x1, y1, (x2 - x1), (y2 - y1), 3f * scale);
		painter.closePath();
	}

	private UIColor getColor(UIResourceFactory factory, boolean accepted) {
		if( accepted ) {
			if( this.acceptedColor == null || this.acceptedColor.isDisposed() ) {
				this.acceptedColor = factory.createColor(ACCEPTED_COLOR);
			}
			return this.acceptedColor;
		}
		if( this.expectedColor == null || this.expectedColor.isDisposed() ) {
			this.expectedColor = factory.createColor(EXPECTED_COLOR);
		}
		return this.expectedColor;
	}

	public void dispose() {
		if( this.expectedColor != null && !this.expectedColor.isDisposed() ) {
			this.expectedColor.dispose();
		}
		if( this.acceptedColor != null && !this.acceptedColor.isDisposed() ) {
			this.acceptedColor.dispose();
		}
	}
}
