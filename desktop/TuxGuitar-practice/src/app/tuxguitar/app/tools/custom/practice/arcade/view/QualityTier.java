package app.tuxguitar.app.tools.custom.practice.arcade.view;

/**
 * How much decoration the view pays for (spec 003 FR-053 to FR-055, visual-design.md "Rendering budget and adaptive quality"). A tier changes only the
 * decoration and the cost of painting it: the hit line, the events, their string and fret, the chord bar and diagram, the camera, the accessibility
 * cues and the verdicts are the same at every tier, at the same instants.
 */
public enum QualityTier {
	/** Glow, trails and soft shadows, the best antialiasing, the field at full resolution. */
	FULL(true, true, true, true, 1.0),
	/** A simpler glow only; cheaper antialiasing. */
	BALANCED(true, false, false, true, 1.0),
	/** No decoration; the field may be drawn at a lower resolution and scaled up, the text and the controls never. */
	PERFORMANCE(false, false, false, false, 0.6);

	private final boolean glow;
	private final boolean trails;
	private final boolean shadows;
	private final boolean antialias;
	private final double fieldResolution;

	QualityTier(boolean glow, boolean trails, boolean shadows, boolean antialias, double fieldResolution) {
		this.glow = glow;
		this.trails = trails;
		this.shadows = shadows;
		this.antialias = antialias;
		this.fieldResolution = fieldResolution;
	}

	public boolean glow() {
		return this.glow;
	}

	public boolean trails() {
		return this.trails;
	}

	public boolean shadows() {
		return this.shadows;
	}

	/** True when the painter should antialias the field. */
	public boolean antialias() {
		return this.antialias;
	}

	/** The share of the field's resolution to paint at (1 = native). Text, hit line and controls are always native. */
	public double fieldResolution() {
		return this.fieldResolution;
	}

	/** The next tier down in cost, or this one at the bottom. */
	public QualityTier cheaper() {
		return this == FULL ? BALANCED : PERFORMANCE;
	}

	/** The next tier up in quality, or this one at the top. */
	public QualityTier richer() {
		return this == PERFORMANCE ? BALANCED : FULL;
	}
}
