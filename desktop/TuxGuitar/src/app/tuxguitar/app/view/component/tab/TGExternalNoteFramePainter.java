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
import app.tuxguitar.ui.resource.UIRectangle;
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
		TGMeasureImpl measure = displayedMeasure(notes, trackSelection);
		if( measure == null ) {
			return;
		}
		painter.setForeground(getColor(factory, accepted));
		painter.setLineWidth(Math.max(1f, 1.5f * layout.getScale()));
		painter.setAntialias(true);
		float scale = layout.getScale();
		if( (layout.getStyle() & TGLayout.DISPLAY_SCORE) != 0 ) {
			float[] box = scoreFrame(layout, measure, notes);
			paintFrame(painter, box[0], box[1], box[2], box[3], scale);
		}
		if( (layout.getStyle() & TGLayout.DISPLAY_TABLATURE) != 0 ) {
			float[] box = tablatureFrame(layout, measure, notes);
			paintFrame(painter, box[0], box[1], box[2], box[3], scale);
		}
	}

	/**
	 * The box, in the coordinates of the score canvas, of the frames {@link #paint} draws around these notes (the score and the tablature frames
	 * together), or null when it would draw none. Reads only.
	 */
	public static UIRectangle frameBounds(TGLayout layout, List<TGNote> notes, int trackSelection) {
		TGMeasureImpl measure = displayedMeasure(notes, trackSelection);
		if( measure == null ) {
			return null;
		}
		float[] union = null;
		if( (layout.getStyle() & TGLayout.DISPLAY_SCORE) != 0 ) {
			union = scoreFrame(layout, measure, notes);
		}
		if( (layout.getStyle() & TGLayout.DISPLAY_TABLATURE) != 0 ) {
			float[] box = tablatureFrame(layout, measure, notes);
			union = (union == null ? box : new float[] { Math.min(union[0], box[0]), Math.min(union[1], box[1]), Math.max(union[2], box[2]), Math.max(union[3], box[3]) });
		}
		return (union == null ? null : new UIRectangle(union[0], union[1], union[2] - union[0], union[3] - union[1]));
	}

	private static float[] scoreFrame(TGLayout layout, TGMeasureImpl measure, List<TGNote> notes) {
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
		return new float[] { x1, y1, x2, y2 };
	}

	private static float[] tablatureFrame(TGLayout layout, TGMeasureImpl measure, List<TGNote> notes) {
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
		return new float[] { x1, y1, x2, y2 };
	}

	/** The measure of the notes when it is displayed (notes of one event share a beat, hence a measure), else null. */
	private static TGMeasureImpl displayedMeasure(List<TGNote> notes, int trackSelection) {
		if( notes == null || notes.isEmpty() || !(notes.get(0) instanceof TGNoteImpl) ) {
			return null;
		}
		TGMeasureImpl measure = ((TGNoteImpl) notes.get(0)).getMeasureImpl();
		if( measure == null || measure.isOutOfBounds() || (trackSelection >= 0 && measure.getTrack().getNumber() != trackSelection) ) {
			return null;
		}
		return measure;
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
