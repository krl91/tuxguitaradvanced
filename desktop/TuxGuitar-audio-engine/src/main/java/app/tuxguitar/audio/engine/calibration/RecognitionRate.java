package app.tuxguitar.audio.engine.calibration;

/** Events of the calibration piece recognised over events of the piece, for the notes, the chords and together (spec 013, FR-006, FR-008). */
public final class RecognitionRate {

	/** The share of events from which a profile is green (FR-008). */
	public static final int THRESHOLD_PERCENT = 60;

	private final int notesAccepted;
	private final int notesTotal;
	private final int chordsAccepted;
	private final int chordsTotal;

	public RecognitionRate(int notesAccepted, int notesTotal, int chordsAccepted, int chordsTotal) {
		if (notesAccepted < 0 || chordsAccepted < 0 || notesAccepted > notesTotal || chordsAccepted > chordsTotal) {
			throw new IllegalArgumentException("accepted events cannot exceed the events of the piece");
		}
		this.notesAccepted = notesAccepted;
		this.notesTotal = notesTotal;
		this.chordsAccepted = chordsAccepted;
		this.chordsTotal = chordsTotal;
	}

	public int getNotesAccepted() {
		return this.notesAccepted;
	}

	public int getNotesTotal() {
		return this.notesTotal;
	}

	public int getChordsAccepted() {
		return this.chordsAccepted;
	}

	public int getChordsTotal() {
		return this.chordsTotal;
	}

	public int getAccepted() {
		return this.notesAccepted + this.chordsAccepted;
	}

	public int getTotal() {
		return this.notesTotal + this.chordsTotal;
	}

	public double totalPercent() {
		return percent(getAccepted(), getTotal());
	}

	public double notesPercent() {
		return percent(this.notesAccepted, this.notesTotal);
	}

	public double chordsPercent() {
		return percent(this.chordsAccepted, this.chordsTotal);
	}

	private static double percent(int accepted, int total) {
		return total == 0 ? 0.0 : 100.0 * accepted / total;
	}

	/** At least 60 % of the events, counted in whole numbers (exactly 60 % counts). */
	public boolean meetsThreshold() {
		return getTotal() > 0 && getAccepted() * 100 >= THRESHOLD_PERCENT * getTotal();
	}

	@Override
	public boolean equals(Object other) {
		if (!(other instanceof RecognitionRate)) {
			return false;
		}
		RecognitionRate rate = (RecognitionRate) other;
		return rate.notesAccepted == this.notesAccepted && rate.notesTotal == this.notesTotal && rate.chordsAccepted == this.chordsAccepted
				&& rate.chordsTotal == this.chordsTotal;
	}

	@Override
	public int hashCode() {
		return ((this.notesAccepted * 31 + this.notesTotal) * 31 + this.chordsAccepted) * 31 + this.chordsTotal;
	}

	@Override
	public String toString() {
		return getAccepted() + "/" + getTotal() + " (notes " + this.notesAccepted + "/" + this.notesTotal + ", chords " + this.chordsAccepted + "/" + this.chordsTotal + ")";
	}
}
