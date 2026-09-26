package app.tuxguitar.app.tools.custom.practice.model;

import java.util.Collections;
import java.util.List;

/**
 * Ordered, immutable practice events of one Practice Track, built once per session start.
 */
public final class PracticeSequence {

	private final List<PracticeEvent> events;
	private final int startIndex;
	private final TrackTuning trackTuning;

	PracticeSequence(List<PracticeEvent> events, int startIndex, TrackTuning trackTuning) {
		this.events = Collections.unmodifiableList(events);
		this.startIndex = startIndex;
		this.trackTuning = trackTuning;
	}

	public List<PracticeEvent> getEvents() {
		return this.events;
	}

	/** Index of the first event to practise (FR-004); 0 by default. */
	public int getStartIndex() {
		return this.startIndex;
	}

	public TrackTuning getTrackTuning() {
		return this.trackTuning;
	}

	public boolean isEmpty() {
		return this.events.isEmpty();
	}
}
