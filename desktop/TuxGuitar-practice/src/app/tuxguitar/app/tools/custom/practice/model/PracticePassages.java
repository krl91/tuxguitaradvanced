package app.tuxguitar.app.tools.custom.practice.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import app.tuxguitar.song.models.TGMeasure;

/**
 * The practice sequence cut into passages (owner policy 2026-09-28, FR-088): runs of events in
 * played order covering at most a fixed number of played measures, counted from the start event.
 * A playback jump (repeat, alternate ending) or a repeat-open sign always starts a new passage, so
 * each played occurrence of a repeated section is practised separately. Events before the start
 * event belong to no passage. Immutable.
 */
public final class PracticePassages {

	/** One passage: sequence indexes {@code [firstIndex, endIndex)}. */
	public static final class Passage {
		private final int number;
		private final int firstIndex;
		private final int endIndex;
		private final int judgedCount;
		private final int firstMeasure;
		private final int lastMeasure;

		Passage(int number, int firstIndex, int endIndex, int judgedCount, int firstMeasure, int lastMeasure) {
			this.number = number;
			this.firstIndex = firstIndex;
			this.endIndex = endIndex;
			this.judgedCount = judgedCount;
			this.firstMeasure = firstMeasure;
			this.lastMeasure = lastMeasure;
		}

		/** 1-based number of the passage in this practice run. */
		public int getNumber() {
			return this.number;
		}

		public int getFirstIndex() {
			return this.firstIndex;
		}

		/** Exclusive. */
		public int getEndIndex() {
			return this.endIndex;
		}

		/** Events judged from the audio (expected notes and chords); the error-rate denominator. */
		public int getJudgedCount() {
			return this.judgedCount;
		}

		/** 1-based score measure number of the first event. */
		public int getFirstMeasure() {
			return this.firstMeasure;
		}

		/** 1-based score measure number of the last event. */
		public int getLastMeasure() {
			return this.lastMeasure;
		}
	}

	private final List<Passage> passages;
	private final int[] passageOfEvent;

	private PracticePassages(List<Passage> passages, int[] passageOfEvent) {
		this.passages = Collections.unmodifiableList(passages);
		this.passageOfEvent = passageOfEvent;
	}

	/** Expected notes and chords are judged; rests, auto-passed and unsupported multi-note events are not. */
	public static boolean isJudged(PracticeEvent event) {
		return (event.getKind() == PracticeEvent.Kind.EXPECTED_NOTE || event.getKind() == PracticeEvent.Kind.EXPECTED_CHORD);
	}

	/**
	 * @param measuresPerPassage played measures per passage, at least 1
	 */
	public static PracticePassages build(PracticeSequence sequence, int measuresPerPassage) {
		if( measuresPerPassage < 1 ) {
			throw new IllegalArgumentException("measuresPerPassage < 1");
		}
		List<PracticeEvent> events = sequence.getEvents();
		int[] passageOfEvent = new int[events.size()];
		Arrays.fill(passageOfEvent, -1);
		List<Passage> passages = new ArrayList<Passage>();
		int first = -1;
		int firstPlayIndex = 0;
		for( int i = sequence.getStartIndex() ; i < events.size() ; i ++ ) {
			PracticeEvent event = events.get(i);
			if( first < 0 ) {
				first = i;
				firstPlayIndex = event.getPlayIndex();
			} else if( startsPassage(events.get(i - 1), event, firstPlayIndex, measuresPerPassage) ) {
				passages.add(passage(events, passages.size(), first, i));
				first = i;
				firstPlayIndex = event.getPlayIndex();
			}
			passageOfEvent[i] = passages.size();
		}
		if( first >= 0 ) {
			passages.add(passage(events, passages.size(), first, events.size()));
		}
		return new PracticePassages(passages, passageOfEvent);
	}

	private static boolean startsPassage(PracticeEvent previous, PracticeEvent event, int firstPlayIndex, int measuresPerPassage) {
		int playStep = event.getPlayIndex() - previous.getPlayIndex();
		if( playStep == 0 ) {
			return false;
		}
		if( event.getPlayIndex() - firstPlayIndex >= measuresPerPassage ) {
			return true;
		}
		// played measures and score measures advanced differently: playback jumped in between
		if( event.getMeasureIndex() - previous.getMeasureIndex() != playStep ) {
			return true;
		}
		TGMeasure measure = (event.getBeat() != null ? event.getBeat().getMeasure() : null);
		return (measure != null && measure.getHeader().isRepeatOpen());
	}

	private static Passage passage(List<PracticeEvent> events, int index, int first, int end) {
		int judged = 0;
		for( int i = first ; i < end ; i ++ ) {
			if( isJudged(events.get(i)) ) {
				judged ++;
			}
		}
		return new Passage(index + 1, first, end, judged, events.get(first).getMeasureIndex() + 1, events.get(end - 1).getMeasureIndex() + 1);
	}

	public List<Passage> getPassages() {
		return this.passages;
	}

	/** Index into {@link #getPassages()} of the passage holding the event, or -1 (before the start event, or out of range). */
	public int passageOf(int sequenceIndex) {
		if( sequenceIndex < 0 || sequenceIndex >= this.passageOfEvent.length ) {
			return -1;
		}
		return this.passageOfEvent[sequenceIndex];
	}
}
