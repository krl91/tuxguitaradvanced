package app.tuxguitar.app.tools.custom.practice.arcade.view;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import app.tuxguitar.app.tools.custom.practice.model.PracticeEvent;
import app.tuxguitar.app.tools.custom.practice.session.FollowTimeline;
import app.tuxguitar.song.models.TGBeat;
import app.tuxguitar.song.models.TGChord;
import app.tuxguitar.song.models.TGNote;
import app.tuxguitar.song.models.TGStroke;

/**
 * An event as the view needs it (spec 003 visual-design.md): built from a {@link PracticeEvent} and the {@link FollowTimeline} of the following
 * mode, nothing else - the view never reads the score a second time and has no tempo or repeat logic of its own. The positions are the tablature's
 * instructions (the audio never verifies string or fret), the onset is the written one after the speed, the tempo changes and the repeats.
 * Immutable.
 */
public final class ArcadeRenderEvent {

	public enum Kind {
		/** One note: a coloured marker resolving to one string and fret. */
		NOTE,
		/** New notes on one instant: one timing bar and one cartouche, never several flying notes. */
		CHORD,
		/** An event that passes by itself (a dead note, a harmonic, a bend only written...): shown as such, not as a recognised note. */
		AUTOMATIC,
		/** Several notes with a pitch-changing effect: not judged, shown as such, never disguised as a chord. */
		UNSUPPORTED
	}

	public enum Stroke {
		NONE,
		DOWN,
		UP
	}

	/** A written position: a TuxGuitar string number (1 is the highest string) and a fret (0 is open). */
	public static final class Position {
		private final int tgString;
		private final int fret;

		Position(int tgString, int fret) {
			this.tgString = tgString;
			this.fret = fret;
		}

		public int tgString() {
			return this.tgString;
		}

		public int fret() {
			return this.fret;
		}
	}

	private final int index;
	private final Kind kind;
	private final long onsetNanos;
	private final List<Position> positions;
	private final List<Integer> deadStrings;
	private final String chordName;
	private final Stroke stroke;
	private final boolean duplicatePitches;
	private final int[] requiredPitches;
	private final int metadataFirstFret;
	private final int metadataStrings;
	private final ChordNameResolver.Display display;
	private final BendMark bend;
	private final List<TechniqueMark> techniques;
	private final boolean harmonic;

	private ArcadeRenderEvent(int index, Kind kind, long onsetNanos, List<Position> positions, List<Integer> deadStrings, String chordName, Stroke stroke,
			boolean duplicatePitches, int[] requiredPitches, int metadataFirstFret, int metadataStrings,
			ChordNameResolver.Display display, BendMark bend, List<TechniqueMark> techniques, boolean harmonic) {
		this.index = index;
		this.kind = kind;
		this.onsetNanos = onsetNanos;
		this.positions = Collections.unmodifiableList(positions);
		this.deadStrings = Collections.unmodifiableList(deadStrings);
		this.chordName = chordName;
		this.stroke = stroke;
		this.duplicatePitches = duplicatePitches;
		this.requiredPitches = requiredPitches;
		this.metadataFirstFret = metadataFirstFret;
		this.metadataStrings = metadataStrings;
		this.display = display;
		this.bend = bend;
		this.techniques = Collections.unmodifiableList(techniques);
		this.harmonic = harmonic;
	}

	/**
	 * @param index the index of the event in the sequence the timeline was built from
	 * @param event the practice event
	 * @param timeline the timeline of the following mode for the chosen speed
	 */
	public static ArcadeRenderEvent of(int index, PracticeEvent event, FollowTimeline timeline) {
		return of(index, event, null, timeline);
	}

	/** @param next the next event of the sequence, or null: where a hammer-on, a pull-off or a slide goes (FR-078) */
	public static ArcadeRenderEvent of(int index, PracticeEvent event, PracticeEvent next, FollowTimeline timeline) {
		long onset = timeline.targetNanos(index);
		List<Position> positions = new ArrayList<Position>();
		List<Integer> dead = new ArrayList<Integer>();
		Kind kind;
		switch (event.getKind()) {
			case EXPECTED_NOTE:
				kind = Kind.NOTE;
				positions.add(new Position(event.getStringNumber(), event.getFret()));
				break;
			case EXPECTED_CHORD:
				kind = Kind.CHORD;
				for (PracticeEvent.ChordNote note : event.getChordNotes()) {
					positions.add(new Position(note.getStringNumber(), note.getFret()));
				}
				for (TGNote note : event.getNotes()) {
					if (note.getEffect().isDeadNote() && !dead.contains(note.getString())) {
						dead.add(note.getString());
					}
				}
				break;
			case MULTI_NOTE:
				kind = Kind.UNSUPPORTED;
				for (TGNote note : event.getNotes()) {
					positions.add(new Position(note.getString(), note.getValue()));
				}
				break;
			default:
				kind = Kind.AUTOMATIC;
				if (event.getStringNumber() > 0) {
					positions.add(new Position(event.getStringNumber(), Math.max(0, event.getFret())));
				}
				break;
		}
		TGChord metadata = (event.getBeat() != null && event.getBeat().isChordBeat() ? event.getBeat().getChord() : null);
		ChordNameResolver.Display display = null;
		if (kind == Kind.CHORD) {
			// the identity of the chord is resolved here, from what the score writes, before any renderer sees it
			int[] pitches = new int[event.getChordNotes().size()];
			for (int i = 0; i < pitches.length; i++) {
				pitches[i] = event.getChordNotes().get(i).getMidi();
			}
			int key = (event.getBeat() != null && event.getBeat().getMeasure() != null) ? event.getBeat().getMeasure().getKeySignature() : 0;
			display = ChordNameResolver.resolve(chordNameOf(event.getBeat()), pitches, key);
		}
		return new ArcadeRenderEvent(index, kind, onset, positions, dead, chordNameOf(event.getBeat()), strokeOf(event.getBeat()), event.hasDuplicatePitches(),
				event.getRequiredPitches(), metadata != null ? metadata.getFirstFret() : 0, metadata != null ? metadata.countStrings() : 0, display,
				kind == Kind.NOTE ? BendMark.of(event.getBendPath()) : null, kind == Kind.NOTE ? TechniqueMark.of(event, next) : Collections.<TechniqueMark>emptyList(),
				kind == Kind.AUTOMATIC && event.getAutoPassReason() == PracticeEvent.AutoPassReason.HARMONIC);
	}

	/** The name written with the chord, when it is not blank: never made up from the pitches. */
	private static String chordNameOf(TGBeat beat) {
		if (beat == null || !beat.isChordBeat()) {
			return null;
		}
		TGChord chord = beat.getChord();
		String name = (chord != null ? chord.getName() : null);
		return (name == null || name.trim().isEmpty()) ? null : name.trim();
	}

	private static Stroke strokeOf(TGBeat beat) {
		if (beat == null || beat.getStroke() == null) {
			return Stroke.NONE;
		}
		switch (beat.getStroke().getDirection()) {
			case TGStroke.STROKE_DOWN:
				return Stroke.DOWN;
			case TGStroke.STROKE_UP:
				return Stroke.UP;
			default:
				return Stroke.NONE;
		}
	}

	public int index() {
		return this.index;
	}

	/** The techniques written on a single note (FR-078), none for anything else. */
	public List<TechniqueMark> techniques() {
		return this.techniques;
	}

	/** An automatic event that is a harmonic (FR-078). */
	public boolean isHarmonic() {
		return this.harmonic;
	}

	/** The bend written for a single note (FR-077), or null: a note without a rising path, a chord and an automatic event have none. */
	public BendMark bend() {
		return this.bend;
	}

	public Kind kind() {
		return this.kind;
	}

	/** The written onset on the timeline, in nanoseconds from its start. */
	public long onsetNanos() {
		return this.onsetNanos;
	}

	public List<Position> positions() {
		return this.positions;
	}

	/** Strings written as muted in the chord (an explicit mark); a string that is just not written is not in this list. */
	public List<Integer> deadStrings() {
		return this.deadStrings;
	}

	/**
	 * What the diagram of a chord shows as its identity and where that comes from (the written name, a name derived from the written notes, or the notes
	 * themselves); null for anything that is not a chord.
	 */
	public ChordNameResolver.Display display() {
		return this.display;
	}

	/** The name written on the chord, or null. */
	public String chordName() {
		return this.chordName;
	}

	public Stroke stroke() {
		return this.stroke;
	}

	/** True when two written notes have the same pitch: nothing can be claimed for each of them separately. */
	public boolean hasDuplicatePitches() {
		return this.duplicatePitches;
	}

	/** The distinct sounding pitches to be heard (a chord), possibly empty. */
	public int[] requiredPitches() {
		return this.requiredPitches.clone();
	}

	/** The first fret of the chord diagram written with the chord, or 0 when none is written: a hint, used only when it fits the written positions. */
	public int metadataFirstFret() {
		return this.metadataFirstFret;
	}

	/** The number of strings of the written chord diagram, or 0 when none is written. */
	public int metadataStrings() {
		return this.metadataStrings;
	}

	public int minFret() {
		int min = Integer.MAX_VALUE;
		for (Position position : this.positions) {
			min = Math.min(min, position.fret);
		}
		return this.positions.isEmpty() ? 0 : min;
	}

	public int maxFret() {
		int max = 0;
		for (Position position : this.positions) {
			max = Math.max(max, position.fret);
		}
		return max;
	}
}
