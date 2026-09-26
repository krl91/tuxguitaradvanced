package app.tuxguitar.app.tools.custom.practice.model;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import app.tuxguitar.app.tools.custom.practice.model.PracticeEvent.AutoPassReason;
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

/**
 * Builds the practice sequence of one track in playback order, using TuxGuitar's own
 * repeat/alternate-ending traversal ({@link MidiRepeatController}). Classification follows
 * data-model.md "PracticeEvent" (research R3).
 */
public final class PracticeSequenceBuilder {

	private PracticeSequenceBuilder() {
	}

	public static PracticeSequence build(TGSong song, TGTrack track, TrackTuning tuning) {
		return build(song, track, tuning, 0);
	}

	public static PracticeSequence build(TGSong song, TGTrack track, TrackTuning tuning, int startIndex) {
		List<PracticeEvent> events = new ArrayList<PracticeEvent>();
		Builder builder = new Builder(tuning, events);
		MidiRepeatController controller = new MidiRepeatController(song, -1, -1);
		int playIndex = 0;
		while( !controller.finished() ) {
			int measureIndex = controller.getIndex();
			controller.process();
			if( controller.shouldPlay() ) {
				builder.addMeasure(track.getMeasure(measureIndex), measureIndex, playIndex);
				playIndex ++;
			}
		}
		int resolvedStart = (startIndex >= 0 && startIndex < events.size() ? startIndex : 0);
		return new PracticeSequence(events, resolvedStart, tuning);
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
		private int previousExpectedMidi = PracticeEvent.NO_PITCH;

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
					boolean allDead = true;
					for( TGNote note : sounding ) {
						allDead &= note.getEffect().isDeadNote();
					}
					add(allDead ? Kind.AUTO_PASS : Kind.MULTI_NOTE, allDead ? AutoPassReason.DEAD_ONLY_CHORD : null,
							playIndex, beat, measureIndex, voiceIndex, 0, PracticeEvent.NO_PITCH, PracticeEvent.NO_PITCH,
							false, EnumSet.noneOf(Technique.class));
				} else {
					addSingle(newNotes.get(0), playIndex, beat, measureIndex, voiceIndex);
				}
			}
		}

		private void addSingle(TGNote note, int playIndex, TGBeat beat, int measureIndex, int voiceIndex) {
			TGNoteEffect effect = note.getEffect();
			int string = note.getString();
			if( effect.isGrace() ) {
				int graceFret = effect.getGrace().getFret();
				if( effect.getGrace().isDead() ) {
					add(Kind.AUTO_PASS, AutoPassReason.GRACE_WITHOUT_PITCH, playIndex, beat, measureIndex, voiceIndex,
							string, graceFret, PracticeEvent.NO_PITCH, true, EnumSet.noneOf(Technique.class));
				} else {
					add(Kind.EXPECTED_NOTE, null, playIndex, beat, measureIndex, voiceIndex, string, graceFret,
							this.tuning.expectedMidi(string, graceFret), true, EnumSet.noneOf(Technique.class));
				}
			}
			AutoPassReason reason = autoPassReason(effect);
			int fret = note.getValue();
			if( reason != null ) {
				add(Kind.AUTO_PASS, reason, playIndex, beat, measureIndex, voiceIndex, string, fret,
						PracticeEvent.NO_PITCH, false, techniques(effect));
			} else {
				add(Kind.EXPECTED_NOTE, null, playIndex, beat, measureIndex, voiceIndex, string, fret,
						this.tuning.expectedMidi(string, fret), false, techniques(effect));
			}
		}

		private void add(Kind kind, AutoPassReason reason, int playIndex, TGBeat beat, int measureIndex,
				int voiceIndex, int string, int fret, int expectedMidi, boolean grace, Set<Technique> techniques) {
			if( kind == Kind.EXPECTED_NOTE && (expectedMidi < 0 || expectedMidi > 127) ) {
				throw new IllegalStateException("expected pitch outside MIDI range: " + expectedMidi);
			}
			boolean sameAsPrevious = (kind == Kind.EXPECTED_NOTE && expectedMidi == this.previousExpectedMidi);
			this.events.add(new PracticeEvent(kind, reason, this.events.size(), playIndex, beat, measureIndex,
					voiceIndex, string, fret, expectedMidi, grace, techniques, sameAsPrevious));
			if( kind == Kind.EXPECTED_NOTE ) {
				this.previousExpectedMidi = expectedMidi;
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
