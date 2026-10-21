package app.tuxguitar.app.tools.custom.practice.arcade.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import app.tuxguitar.app.tools.custom.practice.model.PracticeEvent;
import app.tuxguitar.app.tools.custom.practice.model.PracticeSequence;

/**
 * Chooses the sounds a level of Arcade keeps (spec 014, FR-006 to FR-009, contract arcade-levels): pure and deterministic. The notes and the chords each have their
 * own share; for {@code n} sounds of a kind and a share of {@code s} percent, {@code k} = {@code s x n / 100} rounded half up and at least one (none when the share
 * is 0), and the sounds kept are those of rank {@code floor(i x n / k)}, {@code i} = 0 to {@code k - 1}: <b>the first sound of the kind is always kept</b> and the
 * others are spread evenly after it, by rank in the piece and not by time. A chord is kept or removed whole. When the shares would leave nothing, one chord is kept,
 * so a run is never empty. The events that are not judged ({@code AUTO_PASS}) are not sounds and stay as they are; a multi-note event counts as a chord. The
 * sounds removed are given back, in order, to be heard in the backing (FR-015).
 */
public final class ArcadeLevelFilter {

	/** The sequence of the sounds kept and the sounds removed, in the order of the piece. */
	public static final class Result {
		private final PracticeSequence kept;
		private final List<PracticeEvent> removed;

		Result(PracticeSequence kept, List<PracticeEvent> removed) {
			this.kept = kept;
			this.removed = Collections.unmodifiableList(removed);
		}

		/** The sequence the run is made of. */
		public PracticeSequence kept() {
			return this.kept;
		}

		/** The sounds the level removed, in order; empty at level 5. */
		public List<PracticeEvent> removed() {
			return this.removed;
		}
	}

	private ArcadeLevelFilter() {
	}

	public static Result apply(PracticeSequence full, ArcadeLevel level) {
		List<PracticeEvent> events = full.getEvents();
		if (level.keepsEverything() || events.isEmpty()) {
			return new Result(full, new ArrayList<PracticeEvent>());
		}
		List<Integer> notes = new ArrayList<Integer>();
		List<Integer> chords = new ArrayList<Integer>();
		for (int i = 0; i < events.size(); i++) {
			PracticeEvent.Kind kind = events.get(i).getKind();
			if (kind == PracticeEvent.Kind.EXPECTED_NOTE) {
				notes.add(i);
			} else if (kind == PracticeEvent.Kind.EXPECTED_CHORD || kind == PracticeEvent.Kind.MULTI_NOTE) {
				chords.add(i);
			}
		}
		boolean[] keep = new boolean[events.size()];
		for (int i = 0; i < keep.length; i++) {
			keep[i] = events.get(i).getKind() == PracticeEvent.Kind.AUTO_PASS;
		}
		int kept = mark(keep, notes, level.notePercent()) + mark(keep, chords, level.chordPercent());
		if (kept == 0 && !chords.isEmpty()) {
			keep[chords.get(0)] = true;
		}
		List<PracticeEvent> removed = new ArrayList<PracticeEvent>();
		for (int i = 0; i < keep.length; i++) {
			if (!keep[i]) {
				removed.add(events.get(i));
			}
		}
		return new Result(full.keeping(keep), removed);
	}

	/** Marks the sounds of one kind that the share keeps, and tells how many. */
	private static int mark(boolean[] keep, List<Integer> indices, int percent) {
		int n = indices.size();
		if (n == 0 || percent <= 0) {
			return 0;
		}
		int k = Math.max(1, (percent * n + 50) / 100);
		k = Math.min(k, n);
		for (int i = 0; i < k; i++) {
			keep[indices.get(i * n / k)] = true;
		}
		return k;
	}
}
