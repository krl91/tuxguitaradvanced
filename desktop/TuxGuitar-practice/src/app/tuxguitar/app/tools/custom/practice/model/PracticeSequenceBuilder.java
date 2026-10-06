package app.tuxguitar.app.tools.custom.practice.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import app.tuxguitar.app.tools.custom.practice.model.PracticeEvent.AutoPassReason;
import app.tuxguitar.app.tools.custom.practice.model.PracticeEvent.ChordNote;
import app.tuxguitar.app.tools.custom.practice.model.PracticeEvent.Kind;
import app.tuxguitar.app.tools.custom.practice.model.PracticeEvent.Technique;
import app.tuxguitar.player.base.MidiRepeatController;
import app.tuxguitar.song.models.TGBeat;
import app.tuxguitar.song.models.TGMeasure;
import app.tuxguitar.song.models.TGNote;
import app.tuxguitar.song.models.TGNoteEffect;
import app.tuxguitar.song.models.TGSong;
import app.tuxguitar.song.models.TGTrack;
import app.tuxguitar.song.models.TGVoice;
import app.tuxguitar.song.models.effects.TGEffectBend;

/**
 * Builds the practice sequence of one track in playback order, using TuxGuitar's own
 * repeat/alternate-ending traversal ({@link MidiRepeatController}). Classification follows
 * data-model.md "PracticeEvent" (research R3).
 */
public final class PracticeSequenceBuilder {

	/** A written bend value is a quarter of a whole tone, 0.5 semitone (TGEffectBend.SEMITONE_LENGTH notwithstanding, research R5 addendum). */
	private static final double SEMITONES_PER_BEND_VALUE = 0.5;

	private PracticeSequenceBuilder() {
	}

	/** Builds the whole track's sequence, starting at its first event. */
	public static PracticeSequence build(TGSong song, TGTrack track, TrackTuning tuning) {
		return build(song, track, tuning, null, 0);
	}

	/**
	 * Builds the sequence and resolves the start from the score cursor (FR-004–FR-006): in the
	 * first played occurrence of the cursor's measure (never rewinding a repeat), the first event
	 * at or after the cursor position, else the first event after that occurrence; the track's
	 * first event when the cursor is not on this track or no event follows it.
	 *
	 * @param caretMeasure the cursor's measure, or null
	 * @param caretPosition the cursor's position in ticks ({@code Caret.getPosition()})
	 */
	public static PracticeSequence build(TGSong song, TGTrack track, TrackTuning tuning, TGMeasure caretMeasure, long caretPosition) {
		List<PracticeEvent> events = new ArrayList<PracticeEvent>();
		Builder builder = new Builder(tuning, events);
		MidiRepeatController controller = new MidiRepeatController(song, -1, -1);
		boolean caretOnTrack = (caretMeasure != null && caretMeasure.getTrack() == track);
		int caretPlayIndex = -1;
		int playIndex = 0;
		while( !controller.finished() ) {
			int measureIndex = controller.getIndex();
			controller.process();
			if( controller.shouldPlay() ) {
				TGMeasure measure = track.getMeasure(measureIndex);
				if( caretOnTrack && caretPlayIndex < 0 && measure == caretMeasure ) {
					caretPlayIndex = playIndex;
				}
				builder.addMeasure(measure, measureIndex, playIndex);
				playIndex ++;
			}
		}
		int startIndex = 0;
		if( caretPlayIndex >= 0 ) {
			startIndex = -1;
			for( PracticeEvent event : events ) {
				if( event.getPlayIndex() > caretPlayIndex
						|| (event.getPlayIndex() == caretPlayIndex && event.getBeat().getStart() >= caretPosition) ) {
					startIndex = event.getSequenceIndex();
					break;
				}
			}
			if( startIndex < 0 ) {
				startIndex = 0;
			}
		}
		return new PracticeSequence(events, startIndex, tuning);
	}

	private static final class Sounding {
		private final List<TGNote> notes;
		private final long end;

		private Sounding(List<TGNote> notes, long end) {
			this.notes = notes;
			this.end = end;
		}
	}

	private static final class Builder {
		private final TrackTuning tuning;
		private final List<PracticeEvent> events;
		/** Pitches of the previous expected note or chord (drives {@code sameAsPrevious}, FR-022). */
		private Set<Integer> previousExpectedPitches = Collections.emptySet();

		private Builder(TrackTuning tuning, List<PracticeEvent> events) {
			this.tuning = tuning;
			this.events = events;
		}

		private void addMeasure(TGMeasure measure, int measureIndex, int playIndex) {
			List<Sounding> earlier = new ArrayList<Sounding>();
			for( TGBeat beat : measure.getBeats() ) {
				long start = beat.getStart();
				List<TGNote> sounding = new ArrayList<TGNote>();
				for( Sounding previous : earlier ) {
					if( previous.end > start ) {
						sounding.addAll(previous.notes);
					}
				}
				List<TGNote> newNotes = new ArrayList<TGNote>();
				int voiceIndex = -1;
				for( int v = 0 ; v < TGBeat.MAX_VOICES ; v ++ ) {
					TGVoice voice = beat.getVoice(v);
					if( voice == null || voice.isEmpty() || voice.isRestVoice() || voice.getNotes().isEmpty() ) {
						continue;
					}
					for( TGNote note : voice.getNotes() ) {
						sounding.add(note);
						if( !note.isTiedNote() ) {
							newNotes.add(note);
							if( voiceIndex < 0 ) {
								voiceIndex = v;
							}
						}
					}
					earlier.add(new Sounding(voice.getNotes(), start + voice.getDuration().getTime()));
				}
				if( newNotes.isEmpty() ) {
					continue;
				}
				if( sounding.size() > 1 ) {
					addSimultaneous(sounding, newNotes, playIndex, beat, measureIndex, voiceIndex);
				} else {
					addSingle(newNotes.get(0), playIndex, beat, measureIndex, voiceIndex);
				}
			}
		}

		/**
		 * Several notes sound together (FR-011, FR-012, FR-077): dead only → auto pass; a new note
		 * with a pitch-changing effect → multi-note event waiting for Skip; otherwise an expected
		 * chord whose required pitches are its new non-dead notes, notes still sounding from earlier
		 * beats being tolerated only.
		 */
		private void addSimultaneous(List<TGNote> sounding, List<TGNote> newNotes, int playIndex, TGBeat beat,
				int measureIndex, int voiceIndex) {
			boolean allDead = true;
			for( TGNote note : sounding ) {
				allDead &= note.getEffect().isDeadNote();
			}
			if( allDead ) {
				add(Kind.AUTO_PASS, AutoPassReason.DEAD_ONLY_CHORD, playIndex, beat, measureIndex, voiceIndex, 0,
						PracticeEvent.NO_PITCH, PracticeEvent.NO_PITCH, false, EnumSet.noneOf(Technique.class), newNotes);
				return;
			}
			List<ChordNote> chordNotes = new ArrayList<ChordNote>();
			boolean unsupported = false;
			for( TGNote note : newNotes ) {
				TGNoteEffect effect = note.getEffect();
				if( effect.isDeadNote() ) {
					continue;
				}
				AutoPassReason reason = autoPassReason(effect);
				unsupported |= (reason != null);
				chordNotes.add(new ChordNote(note, note.getString(), note.getValue(), checkedMidi(note.getString(), note.getValue())));
			}
			if( unsupported ) {
				add(Kind.MULTI_NOTE, null, playIndex, beat, measureIndex, voiceIndex, 0, PracticeEvent.NO_PITCH,
						PracticeEvent.NO_PITCH, false, EnumSet.noneOf(Technique.class), newNotes);
			} else if( chordNotes.isEmpty() ) {
				add(Kind.AUTO_PASS, AutoPassReason.DEAD, playIndex, beat, measureIndex, voiceIndex, 0,
						PracticeEvent.NO_PITCH, PracticeEvent.NO_PITCH, false, EnumSet.noneOf(Technique.class), newNotes);
			} else {
				Set<Integer> required = new LinkedHashSet<Integer>();
				for( ChordNote chordNote : chordNotes ) {
					required.add(chordNote.getMidi());
				}
				Set<Integer> tolerated = new LinkedHashSet<Integer>();
				for( TGNote note : sounding ) {
					if( !newNotes.contains(note) && !note.getEffect().isDeadNote() ) {
						int midi = checkedMidi(note.getString(), note.getValue());
						if( !required.contains(midi) ) {
							tolerated.add(midi);
						}
					}
				}
				boolean sameAsPrevious = this.previousExpectedPitches.containsAll(required);
				// the frame surrounds the whole written chord, dead strings included
				this.events.add(new PracticeEvent(Kind.EXPECTED_CHORD, null, this.events.size(), playIndex, beat,
						measureIndex, voiceIndex, 0, PracticeEvent.NO_PITCH, PracticeEvent.NO_PITCH, false,
						EnumSet.noneOf(Technique.class), sameAsPrevious, newNotes, chordNotes,
						tolerated.stream().mapToInt(Integer::intValue).sorted().toArray()));
				this.previousExpectedPitches = required;
			}
		}

		private int checkedMidi(int string, int fret) {
			int midi = this.tuning.expectedMidi(string, fret);
			if( midi < 0 || midi > 127 ) {
				throw new IllegalStateException("expected pitch outside MIDI range: " + midi);
			}
			return midi;
		}

		private void addSingle(TGNote note, int playIndex, TGBeat beat, int measureIndex, int voiceIndex) {
			TGNoteEffect effect = note.getEffect();
			int string = note.getString();
			if( effect.isGrace() ) {
				int graceFret = effect.getGrace().getFret();
				if( effect.getGrace().isDead() ) {
					add(Kind.AUTO_PASS, AutoPassReason.GRACE_WITHOUT_PITCH, playIndex, beat, measureIndex, voiceIndex,
							string, graceFret, PracticeEvent.NO_PITCH, true, EnumSet.noneOf(Technique.class), List.of(note));
				} else {
					add(Kind.EXPECTED_NOTE, null, playIndex, beat, measureIndex, voiceIndex, string, graceFret,
							this.tuning.expectedMidi(string, graceFret), true, EnumSet.noneOf(Technique.class), List.of(note));
				}
			}
			AutoPassReason reason = autoPassReason(effect);
			int fret = note.getValue();
			if( reason == AutoPassReason.BEND && effect.getBend().getPoints().isEmpty() ) {
				// a bend mark without any point says nothing to follow: a plain note
				add(Kind.EXPECTED_NOTE, null, playIndex, beat, measureIndex, voiceIndex, string, fret,
						this.tuning.expectedMidi(string, fret), false, techniques(effect), List.of(note));
			} else if( reason == AutoPassReason.BEND ) {
				addBent(note, playIndex, beat, measureIndex, voiceIndex);
			} else if( reason != null ) {
				add(Kind.AUTO_PASS, reason, playIndex, beat, measureIndex, voiceIndex, string, fret,
						PracticeEvent.NO_PITCH, false, techniques(effect), List.of(note));
			} else {
				add(Kind.EXPECTED_NOTE, null, playIndex, beat, measureIndex, voiceIndex, string, fret,
						this.tuning.expectedMidi(string, fret), false, techniques(effect), List.of(note));
			}
		}

		/**
		 * A bent note is expected (research R5 addendum): the pitch to hear first is the fretted pitch, or for a
		 * pre-bend the fretted pitch plus the first point of the path (rounded to the nearest semitone); the path
		 * (a value is a quarter of a whole tone, a position a twelfth of the note) is kept for the session and the judge.
		 */
		private void addBent(TGNote note, int playIndex, TGBeat beat, int measureIndex, int voiceIndex) {
			int string = note.getString();
			int fret = note.getValue();
			int base = checkedMidi(string, fret);
			double[] pairs = new double[2 * note.getEffect().getBend().getPoints().size()];
			int i = 0;
			double previousTime = -1.0;
			for( TGEffectBend.BendPoint point : note.getEffect().getBend().getPoints() ) {
				double time = point.getPosition() / (double) TGEffectBend.MAX_POSITION_LENGTH;
				if( time <= previousTime ) {
					// two points at the same position: the later one wins
					i -= 2;
				}
				pairs[i ++] = time;
				pairs[i ++] = point.getValue() * SEMITONES_PER_BEND_VALUE;
				previousTime = time;
			}
			PitchPath path = PitchPath.of(java.util.Arrays.copyOf(pairs, i));
			int expected = (int) Math.round(base + path.getPoints().get(0).getSemitones());
			if( expected < 0 || expected > 127 ) {
				throw new IllegalStateException("expected pitch outside MIDI range: " + expected);
			}
			TGVoice voice = beat.getVoice(voiceIndex);
			double seconds = (voice == null ? 0.0 : beat.getMeasure().getTempo().getTicksInMillis(voice.getDuration().getTime()) / 1000.0);
			boolean sameAsPrevious = this.previousExpectedPitches.contains(expected);
			this.events.add(new PracticeEvent(Kind.EXPECTED_NOTE, null, this.events.size(), playIndex, beat, measureIndex,
					voiceIndex, string, fret, expected, false, techniques(note.getEffect()), sameAsPrevious, List.of(note),
					Collections.<ChordNote>emptyList(), new int[0], path, base, seconds));
			this.previousExpectedPitches = Collections.singleton(expected);
		}

		private void add(Kind kind, AutoPassReason reason, int playIndex, TGBeat beat, int measureIndex,
				int voiceIndex, int string, int fret, int expectedMidi, boolean grace, Set<Technique> techniques,
				List<TGNote> notes) {
			if( kind == Kind.EXPECTED_NOTE && (expectedMidi < 0 || expectedMidi > 127) ) {
				throw new IllegalStateException("expected pitch outside MIDI range: " + expectedMidi);
			}
			// a note already sounding in the previous expected note or chord needs a new articulation (FR-022)
			boolean sameAsPrevious = (kind == Kind.EXPECTED_NOTE && this.previousExpectedPitches.contains(expectedMidi));
			this.events.add(new PracticeEvent(kind, reason, this.events.size(), playIndex, beat, measureIndex,
					voiceIndex, string, fret, expectedMidi, grace, techniques, sameAsPrevious, notes,
					Collections.<ChordNote>emptyList(), new int[0]));
			if( kind == Kind.EXPECTED_NOTE ) {
				this.previousExpectedPitches = Collections.singleton(expectedMidi);
			}
		}

		private static AutoPassReason autoPassReason(TGNoteEffect effect) {
			if( effect.isDeadNote() ) {
				return AutoPassReason.DEAD;
			}
			if( effect.isBend() ) {
				return AutoPassReason.BEND;
			}
			if( effect.isHarmonic() ) {
				return AutoPassReason.HARMONIC;
			}
			if( effect.isTremoloBar() ) {
				return AutoPassReason.TREMOLO_BAR;
			}
			if( effect.isTrill() ) {
				return AutoPassReason.TRILL;
			}
			return null;
		}

		private static Set<Technique> techniques(TGNoteEffect effect) {
			Set<Technique> techniques = EnumSet.noneOf(Technique.class);
			if( effect.isHammer() ) {
				techniques.add(Technique.HAMMER_ON_PULL_OFF);
			}
			if( effect.isSlide() ) {
				techniques.add(Technique.SLIDE);
			}
			if( effect.isVibrato() ) {
				techniques.add(Technique.VIBRATO);
			}
			if( effect.isPalmMute() ) {
				techniques.add(Technique.PALM_MUTE);
			}
			if( effect.isAccentuatedNote() || effect.isHeavyAccentuatedNote() ) {
				techniques.add(Technique.ACCENT);
			}
			if( effect.isLetRing() ) {
				techniques.add(Technique.LET_RING);
			}
			if( effect.isStaccato() ) {
				techniques.add(Technique.STACCATO);
			}
			if( effect.isTremoloPicking() ) {
				techniques.add(Technique.TREMOLO_PICKING);
			}
			return techniques;
		}
	}
}
