package app.tuxguitar.app.tools.custom.practice.model;

import app.tuxguitar.song.models.TGTrack;

/**
 * One structurally compatible track offered in the preparation step. Immutable.
 */
public final class PracticeTrackCandidate {

	private final TGTrack track;
	private final boolean guitarHint;
	private final boolean preselected;

	PracticeTrackCandidate(TGTrack track, boolean guitarHint, boolean preselected) {
		this.track = track;
		this.guitarHint = guitarHint;
		this.preselected = preselected;
	}

	public TGTrack getTrack() {
		return this.track;
	}

	public int getTrackNumber() {
		return this.track.getNumber();
	}

	public String getTrackName() {
		return this.track.getName();
	}

	/** The track's sound is in the General MIDI guitar family; advisory only. */
	public boolean hasGuitarHint() {
		return this.guitarHint;
	}

	public boolean isPreselected() {
		return this.preselected;
	}
}
