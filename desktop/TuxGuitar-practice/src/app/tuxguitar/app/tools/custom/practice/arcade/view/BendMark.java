package app.tuxguitar.app.tools.custom.practice.arcade.view;

import app.tuxguitar.app.tools.custom.practice.model.PitchPath;

/**
 * What the picture shows of a written bend (spec 003 FR-077): how high it goes, whether the note starts already bent (a pre-bend) and whether it comes
 * back down (a release). Built from the pitch path the score writes, never from the audio. Immutable.
 */
public final class BendMark {

	/** Below this many semitones a path is taken as flat. */
	private static final double EPSILON = 0.01;

	private final double peakSemitones;
	private final boolean startsBent;
	private final boolean releases;

	private BendMark(double peakSemitones, boolean startsBent, boolean releases) {
		this.peakSemitones = peakSemitones;
		this.startsBent = startsBent;
		this.releases = releases;
	}

	/** The mark of a path, or null when it never rises above the fretted pitch (or there is no path): nothing is shown then. */
	public static BendMark of(PitchPath path) {
		if (path == null) {
			return null;
		}
		double peak = path.getPeakSemitones();
		if (peak <= EPSILON) {
			return null;
		}
		boolean startsBent = path.getPoints().get(0).getSemitones() > EPSILON;
		boolean releases = path.getTargetSemitones() < peak - EPSILON;
		return new BendMark(peak, startsBent, releases);
	}

	/** The highest the path goes above the fretted pitch, in semitones. */
	public double peakSemitones() {
		return this.peakSemitones;
	}

	/** The note starts already bent. */
	public boolean startsBent() {
		return this.startsBent;
	}

	/** The path ends lower than its peak. */
	public boolean releases() {
		return this.releases;
	}
}
