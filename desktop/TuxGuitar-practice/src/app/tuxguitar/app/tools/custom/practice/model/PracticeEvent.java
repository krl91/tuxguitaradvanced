package app.tuxguitar.app.tools.custom.practice.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import app.tuxguitar.song.models.TGBeat;

/**
 * One step of the practice sequence, in playback order. Immutable.
 * String and fret are the tablature's instruction only; they are never verified from audio.
 */
public final class PracticeEvent {

	public enum Kind {
		EXPECTED_NOTE,
		/** Several notes sound together: not validated, waits for Skip. */
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

	PracticeEvent(Kind kind, AutoPassReason autoPassReason, int sequenceIndex, int playIndex, TGBeat beat,
			int measureIndex, int voiceIndex, int stringNumber, int fret, int expectedMidi, boolean grace,
			Set<Technique> techniques, boolean sameAsPrevious) {
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

	/** True when the expected pitch equals the previous expected note's pitch (re-articulation, FR-022). */
	public boolean isSameAsPrevious() {
		return this.sameAsPrevious;
	}
}
