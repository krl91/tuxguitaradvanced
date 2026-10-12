package app.tuxguitar.app.tools.custom.practice.arcade.view;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import app.tuxguitar.app.tools.custom.practice.model.PracticeEvent;
import app.tuxguitar.song.models.TGNote;
import app.tuxguitar.song.models.TGNoteEffect;

/**
 * What the picture shows of a technique written on a single note (spec 003 FR-078): its kind and, for a hammer-on, a pull-off or a slide, the number of
 * frets to the next note of the track on the same string (positive to the right, negative to the left, 0 when there is none). Built from the written
 * effects, never from the audio. Immutable.
 */
public final class TechniqueMark {

	public enum Kind {
		/** A hammer-on or a pull-off whose direction is not known: the note has no next note on its string (the score writes one mark for both). */
		LEGATO, HAMMER_ON, PULL_OFF, SLIDE, VIBRATO, PALM_MUTE, TREMOLO_PICKING, TAPPING
	}

	private final Kind kind;
	private final int fretDelta;

	private TechniqueMark(Kind kind, int fretDelta) {
		this.kind = kind;
		this.fretDelta = fretDelta;
	}

	/**
	 * The marks of an event, in a fixed order; none for an event that is not a single note.
	 *
	 * @param next the next event of the sequence, or null: where a hammer-on, a pull-off or a slide goes
	 */
	public static List<TechniqueMark> of(PracticeEvent event, PracticeEvent next) {
		if (event.getKind() != PracticeEvent.Kind.EXPECTED_NOTE || event.getNotes().size() != 1) {
			return Collections.emptyList();
		}
		TGNote note = event.getNotes().get(0);
		TGNoteEffect effect = note.getEffect();
		int delta = 0;
		if (next != null && next.getKind() == PracticeEvent.Kind.EXPECTED_NOTE && next.getStringNumber() == event.getStringNumber()) {
			delta = next.getFret() - event.getFret();
		}
		List<TechniqueMark> marks = new ArrayList<TechniqueMark>();
		if (effect.isHammer()) {
			marks.add(new TechniqueMark(delta < 0 ? Kind.PULL_OFF : delta > 0 ? Kind.HAMMER_ON : Kind.LEGATO, delta));
		}
		if (effect.isSlide()) {
			marks.add(new TechniqueMark(Kind.SLIDE, delta));
		}
		if (effect.isVibrato()) {
			marks.add(new TechniqueMark(Kind.VIBRATO, 0));
		}
		if (effect.isPalmMute()) {
			marks.add(new TechniqueMark(Kind.PALM_MUTE, 0));
		}
		if (effect.isTremoloPicking()) {
			marks.add(new TechniqueMark(Kind.TREMOLO_PICKING, 0));
		}
		if (effect.isTapping()) {
			marks.add(new TechniqueMark(Kind.TAPPING, 0));
		}
		return marks;
	}

	public Kind kind() {
		return this.kind;
	}

	/** Frets to the next note of the string: positive to the right, negative to the left, 0 when there is no destination. */
	public int fretDelta() {
		return this.fretDelta;
	}

	/** The letters of the notation: H, P, S, P.M., T; null for the kinds that are drawn without one (the wave, the strokes). */
	public static String letterOf(Kind kind) {
		switch (kind) {
			case LEGATO:
				return "H/P";
			case HAMMER_ON:
				return "H";
			case PULL_OFF:
				return "P";
			case SLIDE:
				return "S";
			case PALM_MUTE:
				return "P.M.";
			case TAPPING:
				return "T";
			default:
				return null;
		}
	}
}
