package app.tuxguitar.app.tools.custom.practice.model;

import java.util.ArrayList;
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

	/**
	 * The written techniques to judge on an event. Besides the event's own (slide, vibrato...), a note that
	 * follows a note carrying a hammer-on / pull-off mark is its destination: it is a hammer-on when it is
	 * higher than the origin and a pull-off when it is lower, with the origin as its partner (spec 002 T069).
	 */
	public List<TechniqueTarget> targetsOf(int index) {
		PracticeEvent event = this.events.get(index);
		List<TechniqueTarget> targets = new ArrayList<TechniqueTarget>(event.getTechniqueTargets());
		if( index > 0 && event.getKind() == PracticeEvent.Kind.EXPECTED_NOTE ) {
			PracticeEvent origin = this.events.get(index - 1);
			if( origin.getKind() == PracticeEvent.Kind.EXPECTED_NOTE && origin.getTechniques().contains(PracticeEvent.Technique.HAMMER_ON_PULL_OFF)
					&& origin.getExpectedMidi() != event.getExpectedMidi() ) {
				targets.add(TechniqueTarget.withPartner(event.getExpectedMidi() > origin.getExpectedMidi() ? PlayingTechnique.HAMMER_ON : PlayingTechnique.PULL_OFF,
						event.getExpectedMidi(), index - 1));
			}
		}
		return Collections.unmodifiableList(targets);
	}

	/**
	 * The events from the given one on, as a sequence of their own that starts at its first event: what a run of the tempo-following mode judges when
	 * the learner starts in the middle of the score. The events keep their measure and their play index, so their times still come from the song; the
	 * destination of a legato ({@link #targetsOf}) is looked for in the events kept, so a note whose origin is before the start is judged as a plain note.
	 */
	public PracticeSequence startingAt(int index) {
		int from = Math.max(0, Math.min(index, this.events.size()));
		return new PracticeSequence(new ArrayList<PracticeEvent>(this.events.subList(from, this.events.size())), 0, this.trackTuning);
	}

	/**
	 * A sequence of this one event only, to judge one recorded sound alone against it (spec 013): the other events are not in the session, so what is
	 * heard cannot be taken for them nor wait behind an earlier miss.
	 */
	public PracticeSequence onlyEvent(int index) {
		if (index < 0 || index >= this.events.size()) {
			throw new IllegalArgumentException("no event " + index);
		}
		List<PracticeEvent> only = new ArrayList<PracticeEvent>();
		only.add(this.events.get(index));
		return new PracticeSequence(only, 0, this.trackTuning);
	}

	public boolean isEmpty() {
		return this.events.isEmpty();
	}
}
