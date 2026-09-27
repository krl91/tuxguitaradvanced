package app.tuxguitar.app.tools.custom.practice.model;

import app.tuxguitar.audio.engine.session.PracticeTunerContext;
import app.tuxguitar.song.models.TGSong;

/**
 * The Practice Track chosen for one session; never saved into the song. Practice cannot start
 * until the learner explicitly accepts the candidate, unless it was already accepted during this
 * run. Preselection and the guitar hint never accept it.
 */
public final class PracticeTrackSelection {

	private final TGSong song;
	private final PracticeTrackCandidate candidate;
	private final PracticeTunerContext memory;
	private boolean accepted;

	public PracticeTrackSelection(TGSong song, PracticeTrackCandidate candidate, PracticeTunerContext memory) {
		this.song = song;
		this.candidate = candidate;
		this.memory = memory;
		this.accepted = memory.isConfirmed(song, candidate.getTrack());
	}

	public PracticeTrackCandidate getCandidate() {
		return this.candidate;
	}

	public boolean isAccepted() {
		return this.accepted;
	}

	/** Explicit learner action: this candidate is the guitar part to practise. */
	public void accept() {
		this.accepted = true;
		this.memory.confirm(this.song, this.candidate.getTrack());
	}
}
