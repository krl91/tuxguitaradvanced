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
 * The tracks a learner can perform in Arcade mode (spec 003 FR-002, FR-027, research R8): any pitched track with practice events, whatever its
 * number of strings (a four-string bass, a seven-string guitar). The wait and following modes keep {@link PracticeTrackCandidates}, which is for six
 * strings. The General MIDI guitar and bass families are an advisory hint; the learner always chooses, and nothing is started on a pre-selection alone.
 */
public final class ArcadeTrackCandidates {

	public enum Exclusion {
		PERCUSSION,
		UNDEFINED_STRING_PITCH,
		NO_PRACTICE_EVENTS
	}

	/** 0-based General MIDI: acoustic bass to synth bass 2 (32 to 39) and the guitar family (24 to 31). */
	private static final int FIRST_GUITAR_PROGRAM = 24;
	private static final int LAST_BASS_PROGRAM = 39;

	private final List<PracticeTrackCandidate> candidates;
	private final Map<TGTrack, Exclusion> exclusions;

	private ArcadeTrackCandidates(List<PracticeTrackCandidate> candidates, Map<TGTrack, Exclusion> exclusions) {
		this.candidates = Collections.unmodifiableList(candidates);
		this.exclusions = Collections.unmodifiableMap(exclusions);
	}

	/** @param caretTrack the track under the score cursor, or null; pre-selected when it can be performed */
	public static ArcadeTrackCandidates evaluate(TGSong song, TGTrack caretTrack) {
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
				if( hasInstrumentProgram(song, track) ) {
					preselected = track;
					break;
				}
			}
		}
		List<PracticeTrackCandidate> candidates = new ArrayList<PracticeTrackCandidate>();
		for( TGTrack track : compatible ) {
			candidates.add(new PracticeTrackCandidate(track, hasInstrumentProgram(song, track), track == preselected));
		}
		return new ArcadeTrackCandidates(candidates, exclusions);
	}

	public List<PracticeTrackCandidate> getCandidates() {
		return this.candidates;
	}

	/** The initially shown candidate, or null when the learner must pick one. It is never started without the learner's confirmation. */
	public PracticeTrackCandidate getPreselected() {
		for( PracticeTrackCandidate candidate : this.candidates ) {
			if( candidate.isPreselected() ) {
				return candidate;
			}
		}
		return null;
	}

	/** Why each non-candidate track was excluded. */
	public Map<TGTrack, Exclusion> getExclusions() {
		return this.exclusions;
	}

	private static Exclusion exclusion(TGSong song, TGTrack track) {
		if( track.isPercussion() ) {
			return Exclusion.PERCUSSION;
		}
		if( track.stringCount() < 1 ) {
			return Exclusion.UNDEFINED_STRING_PITCH;
		}
		for( TGString string : track.getStrings() ) {
			if( string.getValue() < 0 || string.getValue() > 127 ) {
				return Exclusion.UNDEFINED_STRING_PITCH;
			}
		}
		if( PracticeSequenceBuilder.build(song, track, TrackTuning.ofAnyStrings(track)).isEmpty() ) {
			return Exclusion.NO_PRACTICE_EVENTS;
		}
		return null;
	}

	private static boolean hasInstrumentProgram(TGSong song, TGTrack track) {
		Iterator<TGChannel> channels = song.getChannels();
		while( channels.hasNext() ) {
			TGChannel channel = channels.next();
			if( channel.getChannelId() == track.getChannelId() ) {
				return (channel.getProgram() >= FIRST_GUITAR_PROGRAM && channel.getProgram() <= LAST_BASS_PROGRAM);
			}
		}
		return false;
	}
}
