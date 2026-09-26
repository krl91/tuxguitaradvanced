package app.tuxguitar.app.tools.custom.practice.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import app.tuxguitar.song.models.TGChannel;
import app.tuxguitar.song.models.TGSong;
import app.tuxguitar.song.models.TGString;
import app.tuxguitar.song.models.TGTrack;

/**
 * The structurally compatible Practice Track candidates of a song (FR-003, research R4).
 * The General MIDI guitar family is only an advisory hint; the learner always accepts a track.
 */
public final class PracticeTrackCandidates {

	public enum Exclusion {
		PERCUSSION,
		NOT_SIX_STRINGS,
		UNDEFINED_STRING_PITCH,
		NO_PRACTICE_EVENTS
	}

	/** 0-based General MIDI guitar family, as stored by {@code TGChannel.getProgram()}. */
	private static final int FIRST_GUITAR_PROGRAM = 24;
	private static final int LAST_GUITAR_PROGRAM = 31;

	private final List<PracticeTrackCandidate> candidates;
	private final Map<TGTrack, Exclusion> exclusions;

	private PracticeTrackCandidates(List<PracticeTrackCandidate> candidates, Map<TGTrack, Exclusion> exclusions) {
		this.candidates = Collections.unmodifiableList(candidates);
		this.exclusions = Collections.unmodifiableMap(exclusions);
	}

	/**
	 * @param caretTrack the track under the score cursor, or null; preselected when compatible
	 */
	public static PracticeTrackCandidates evaluate(TGSong song, TGTrack caretTrack) {
		List<TGTrack> compatible = new ArrayList<TGTrack>();
		Map<TGTrack, Exclusion> exclusions = new LinkedHashMap<TGTrack, Exclusion>();
		Iterator<TGTrack> tracks = song.getTracks();
		while( tracks.hasNext() ) {
			TGTrack track = tracks.next();
			Exclusion exclusion = exclusion(song, track);
			if( exclusion != null ) {
				exclusions.put(track, exclusion);
			} else {
				compatible.add(track);
			}
		}
		TGTrack preselected = null;
		if( caretTrack != null && compatible.contains(caretTrack) ) {
			preselected = caretTrack;
		} else {
			for( TGTrack track : compatible ) {
				if( hasGuitarProgram(song, track) ) {
					preselected = track;
					break;
				}
			}
		}
		List<PracticeTrackCandidate> candidates = new ArrayList<PracticeTrackCandidate>();
		for( TGTrack track : compatible ) {
			candidates.add(new PracticeTrackCandidate(track, hasGuitarProgram(song, track), track == preselected));
		}
		return new PracticeTrackCandidates(candidates, exclusions);
	}

	public List<PracticeTrackCandidate> getCandidates() {
		return this.candidates;
	}

	/** The initially shown candidate, or null when the learner must pick one. */
	public PracticeTrackCandidate getPreselected() {
		for( PracticeTrackCandidate candidate : this.candidates ) {
			if( candidate.isPreselected() ) {
				return candidate;
			}
		}
		return null;
	}

	/** Why each non-candidate track was excluded (shown when no candidate remains). */
	public Map<TGTrack, Exclusion> getExclusions() {
		return this.exclusions;
	}

	private static Exclusion exclusion(TGSong song, TGTrack track) {
		if( track.isPercussion() ) {
			return Exclusion.PERCUSSION;
		}
		if( track.stringCount() != TrackTuning.STRING_COUNT ) {
			return Exclusion.NOT_SIX_STRINGS;
		}
		for( TGString string : track.getStrings() ) {
			if( string.getValue() < 0 || string.getValue() > 127 ) {
				return Exclusion.UNDEFINED_STRING_PITCH;
			}
		}
		if( PracticeSequenceBuilder.build(song, track, TrackTuning.of(track, null)).isEmpty() ) {
			return Exclusion.NO_PRACTICE_EVENTS;
		}
		return null;
	}

	private static boolean hasGuitarProgram(TGSong song, TGTrack track) {
		Iterator<TGChannel> channels = song.getChannels();
		while( channels.hasNext() ) {
			TGChannel channel = channels.next();
			if( channel.getChannelId() == track.getChannelId() ) {
				return (channel.getProgram() >= FIRST_GUITAR_PROGRAM && channel.getProgram() <= LAST_GUITAR_PROGRAM);
			}
		}
		return false;
	}
}
