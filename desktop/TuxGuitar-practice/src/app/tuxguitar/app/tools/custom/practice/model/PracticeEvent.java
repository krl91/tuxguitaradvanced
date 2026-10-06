package app.tuxguitar.app.tools.custom.practice.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import app.tuxguitar.song.models.TGBeat;
import app.tuxguitar.song.models.TGNote;

/**
 * One step of the practice sequence, in playback order. Immutable.
 * String and fret are the tablature's instruction only; they are never verified from audio.
 */
public final class PracticeEvent {

	public enum Kind {
		EXPECTED_NOTE,
		/**
		 * Several new pitched notes, or a new note over sustained ones, without pitch-changing
		 * effects (FR-077): its distinct pitches are validated together. Until chord matching
		 * exists it waits for Skip, like {@link #MULTI_NOTE}.
		 */
		EXPECTED_CHORD,
		/** Several notes sound together, at least one with a pitch-changing effect: not validated, waits for Skip. */
		MULTI_NOTE,
		AUTO_PASS
	}

	public enum AutoPassReason {
		DEAD,
		DEAD_ONLY_CHORD,
		GRACE_WITHOUT_PITCH,
		BEND,
		HARMONIC,
		TREMOLO_BAR,
		TRILL
	}

	/** Display-only playing techniques; never verified. */
	public enum Technique {
		HAMMER_ON_PULL_OFF,
		SLIDE,
		VIBRATO,
		PALM_MUTE,
		ACCENT,
		LET_RING,
		STACCATO,
		TREMOLO_PICKING
	}

	/** One new pitched note of an expected chord. String and fret are the tablature's instruction only. */
	public static final class ChordNote {
		private final TGNote note;
		private final int stringNumber;
		private final int fret;
		private final int midi;

		ChordNote(TGNote note, int stringNumber, int fret, int midi) {
			this.note = note;
			this.stringNumber = stringNumber;
			this.fret = fret;
			this.midi = midi;
		}

		/** The score note, passed to the practice frame. */
		public TGNote getNote() {
			return this.note;
		}

		public int getStringNumber() {
			return this.stringNumber;
		}

		public int getFret() {
			return this.fret;
		}

		/** Sounding MIDI pitch. */
		public int getMidi() {
			return this.midi;
		}
	}

	/** Value of {@link #getExpectedMidi()} for events without an expected pitch. */
	public static final int NO_PITCH = -1;

	private final Kind kind;
	private final AutoPassReason autoPassReason;
	private final int sequenceIndex;
	private final int playIndex;
	private final TGBeat beat;
	private final int measureIndex;
	private final int voiceIndex;
	private final int stringNumber;
	private final int fret;
	private final int expectedMidi;
	private final boolean grace;
	private final Set<Technique> techniques;
	private final boolean sameAsPrevious;
	private final List<TGNote> notes;
	private final List<ChordNote> chordNotes;
	private final int[] requiredPitches;
	private final int[] toleratedPitches;
	private final PitchPath bendPath;
	private final int bendBaseMidi;
	private final double bendWrittenSeconds;

	PracticeEvent(Kind kind, AutoPassReason autoPassReason, int sequenceIndex, int playIndex, TGBeat beat,
			int measureIndex, int voiceIndex, int stringNumber, int fret, int expectedMidi, boolean grace,
			Set<Technique> techniques, boolean sameAsPrevious) {
		this(kind, autoPassReason, sequenceIndex, playIndex, beat, measureIndex, voiceIndex, stringNumber, fret,
				expectedMidi, grace, techniques, sameAsPrevious, Collections.<TGNote>emptyList(),
				Collections.<ChordNote>emptyList(), new int[0]);
	}

	/**
	 * @param notes the score notes of the event, framed in the score (empty for none)
	 * @param chordNotes for {@link Kind#EXPECTED_CHORD}: its new pitched notes
	 * @param toleratedPitches for {@link Kind#EXPECTED_CHORD}: pitches still sounding from earlier beats
	 */
	PracticeEvent(Kind kind, AutoPassReason autoPassReason, int sequenceIndex, int playIndex, TGBeat beat,
			int measureIndex, int voiceIndex, int stringNumber, int fret, int expectedMidi, boolean grace,
			Set<Technique> techniques, boolean sameAsPrevious, List<TGNote> notes, List<ChordNote> chordNotes,
			int[] toleratedPitches) {
		this(kind, autoPassReason, sequenceIndex, playIndex, beat, measureIndex, voiceIndex, stringNumber, fret, expectedMidi, grace,
				techniques, sameAsPrevious, notes, chordNotes, toleratedPitches, null, NO_PITCH, 0.0);
	}

	/**
	 * @param bendPath the written pitch path of a bent note (semitones above the fretted pitch), or null
	 * @param bendBaseMidi the fretted pitch the path is relative to ({@link #NO_PITCH} without a path)
	 * @param bendWrittenSeconds the written length of the bent note at the tempo of its measure
	 */
	PracticeEvent(Kind kind, AutoPassReason autoPassReason, int sequenceIndex, int playIndex, TGBeat beat,
			int measureIndex, int voiceIndex, int stringNumber, int fret, int expectedMidi, boolean grace,
			Set<Technique> techniques, boolean sameAsPrevious, List<TGNote> notes, List<ChordNote> chordNotes,
			int[] toleratedPitches, PitchPath bendPath, int bendBaseMidi, double bendWrittenSeconds) {
		this.bendPath = bendPath;
		this.bendBaseMidi = bendBaseMidi;
		this.bendWrittenSeconds = bendWrittenSeconds;
		this.kind = kind;
		this.autoPassReason = autoPassReason;
		this.sequenceIndex = sequenceIndex;
		this.playIndex = playIndex;
		this.beat = beat;
		this.measureIndex = measureIndex;
		this.voiceIndex = voiceIndex;
		this.stringNumber = stringNumber;
		this.fret = fret;
		this.expectedMidi = expectedMidi;
		this.grace = grace;
		this.techniques = (techniques.isEmpty() ? Collections.<Technique>emptySet() : Collections.unmodifiableSet(EnumSet.copyOf(techniques)));
		this.sameAsPrevious = sameAsPrevious;
		this.notes = Collections.unmodifiableList(new ArrayList<TGNote>(notes));
		this.chordNotes = Collections.unmodifiableList(new ArrayList<ChordNote>(chordNotes));
		this.requiredPitches = distinctAscending(chordNotes);
		this.toleratedPitches = toleratedPitches.clone();
	}

	private static int[] distinctAscending(List<ChordNote> chordNotes) {
		return chordNotes.stream().mapToInt(ChordNote::getMidi).distinct().sorted().toArray();
	}

	public Kind getKind() {
		return this.kind;
	}

	/** Null unless the kind is {@link Kind#AUTO_PASS}. */
	public AutoPassReason getAutoPassReason() {
		return this.autoPassReason;
	}

	public int getSequenceIndex() {
		return this.sequenceIndex;
	}

	/** Index of the played measure occurrence, distinguishing repeat passes. */
	public int getPlayIndex() {
		return this.playIndex;
	}

	/** The score beat, passed to the practice highlight. */
	public TGBeat getBeat() {
		return this.beat;
	}

	public int getMeasureIndex() {
		return this.measureIndex;
	}

	public int getVoiceIndex() {
		return this.voiceIndex;
	}

	/** Tablature instruction only; 0 when the event has no single note. */
	public int getStringNumber() {
		return this.stringNumber;
	}

	/** Tablature instruction only; {@link #NO_PITCH} when the event has no single note. */
	public int getFret() {
		return this.fret;
	}

	/** Sounding MIDI pitch for {@link Kind#EXPECTED_NOTE}; {@link #NO_PITCH} otherwise. */
	public int getExpectedMidi() {
		return this.expectedMidi;
	}

	public boolean isGrace() {
		return this.grace;
	}

	public Set<Technique> getTechniques() {
		return this.techniques;
	}

	/**
	 * The written techniques of this event with what is needed to judge them (spec 002). For now they
	 * are derived from the display-only set: slide, vibrato, palm mute and tremolo picking, and from the
	 * written bend: a bend, a pre-bend and a release each with the path. Hammer-ons and pull-offs need
	 * the destination note to be told apart (PracticeSequence.targetsOf); harmonics are still auto-passed.
	 */
	public List<TechniqueTarget> getTechniqueTargets() {
		List<TechniqueTarget> targets = new ArrayList<TechniqueTarget>();
		for( Technique technique : this.techniques ) {
			PlayingTechnique mapped = mapped(technique);
			if( mapped != null ) {
				targets.add(TechniqueTarget.of(mapped));
			}
		}
		if( this.bendPath != null ) {
			int targetMidi = (int) Math.round(this.bendBaseMidi + this.bendPath.getPeakSemitones());
			List<PlayingTechnique> bends = new ArrayList<PlayingTechnique>();
			if( this.bendPath.getPoints().get(0).getSemitones() > 0.0 ) {
				bends.add(PlayingTechnique.PRE_BEND);
			}
			if( this.bendPath.getPeakSemitones() > this.bendPath.getPoints().get(0).getSemitones() ) {
				bends.add(PlayingTechnique.BEND);
			}
			if( this.bendPath.getTargetSemitones() < this.bendPath.getPeakSemitones() ) {
				bends.add(PlayingTechnique.RELEASE);
			}
			for( PlayingTechnique bend : bends ) {
				targets.add(TechniqueTarget.withBend(bend, this.bendBaseMidi, targetMidi, this.bendPath));
			}
		}
		return Collections.unmodifiableList(targets);
	}

	private static PlayingTechnique mapped(Technique technique) {
		switch( technique ) {
			case SLIDE:
				return PlayingTechnique.SLIDE;
			case VIBRATO:
				return PlayingTechnique.VIBRATO;
			case PALM_MUTE:
				return PlayingTechnique.PALM_MUTE;
			case TREMOLO_PICKING:
				return PlayingTechnique.TREMOLO_PICKING;
			case ACCENT:
				return PlayingTechnique.ACCENT;
			case LET_RING:
				return PlayingTechnique.LET_RING;
			case STACCATO:
				return PlayingTechnique.STACCATO;
			default:
				return null;
		}
	}

	/** The written pitch path of a bent note (semitones above the fretted pitch, research R5 addendum); null for the other events. */
	public PitchPath getBendPath() {
		return this.bendPath;
	}

	/** The written length of a bent note in seconds at the tempo of its measure (the observation window follows it); 0 without a bend. */
	public double getBendWrittenSeconds() {
		return this.bendWrittenSeconds;
	}

	/** True when the expected pitch equals the previous expected note's pitch (re-articulation, FR-022). */
	public boolean isSameAsPrevious() {
		return this.sameAsPrevious;
	}

	/** The score notes attacked by this event (what the practice frame surrounds); may be empty. */
	public List<TGNote> getNotes() {
		return this.notes;
	}

	/** New pitched notes of an {@link Kind#EXPECTED_CHORD}, in score order; empty otherwise. */
	public List<ChordNote> getChordNotes() {
		return this.chordNotes;
	}

	/** Distinct sounding pitches an expected chord requires, ascending (FR-077, FR-078); empty otherwise. */
	public int[] getRequiredPitches() {
		return this.requiredPitches.clone();
	}

	/** Pitches of an expected chord still sounding from earlier beats: allowed, never required (FR-077). */
	public int[] getToleratedPitches() {
		return this.toleratedPitches.clone();
	}

	/** True when a required pitch of an expected chord is written on several strings (FR-078). */
	public boolean hasDuplicatePitches() {
		return this.requiredPitches.length < this.chordNotes.size();
	}
}
