package app.tuxguitar.app.tools.custom.practice.arcade.view;

/**
 * Every value that shapes a fire or its sparks (spec 019 FR-015, data model "FireParameter"): its key, its kind, its unit, its range and its default for
 * Arcade's fire and for the score fire. Each Arcade default is the value the fire used before this spec (FR-017), the named constant itself where there is
 * one, so that with the defaults every picture is the same. Not here, and fixed whatever the settings (research R4): the reading guard, the seed and the
 * hash that draws each spark, the numbering of the cells, and the rules of the automatic quality tiers.
 */
public enum FireParameter {

	OPACITY_MOST("opacity.most", Section.LOOK, Kind.NUMBER, Unit.SHARE, 0.0, 1.0, FireColours.MOST, FireColours.MOST),
	CELL_COLUMNS("cell.columns", Section.LOOK, Kind.INTEGER, Unit.COLUMNS, 16, 384, FireField.COLUMNS, FireField.COLUMNS),
	CELL_SMALLEST("cell.smallest", Section.LOOK, Kind.NUMBER, Unit.PIXELS, 3.0, 32.0, FireField.SMALLEST, FireField.SMALLEST),
	DISCREET_SHARE("discreet.share", Section.LOOK, Kind.NUMBER, Unit.SHARE, 0.05, 1.0, FlameDrawing.DISCREET, FlameDrawing.DISCREET),

	HEIGHT_CORNERS("height.corners", Section.FLAMES, Kind.NUMBER, Unit.SHARE, 0.02, 1.0, FlameDrawing.CORNER_HEIGHT, 1.0 / 3.0),
	HEIGHT_MIDDLE("height.middle", Section.FLAMES, Kind.NUMBER, Unit.SHARE, 0.0, 1.0, FlameDrawing.MIDDLE, FlameDrawing.MIDDLE),
	HEIGHT_CURVE("height.curve", Section.FLAMES, Kind.NUMBER, Unit.NONE, 0.25, 8.0, 2.0, 2.0),
	BODY_HEAT("body.heat", Section.FLAMES, Kind.NUMBER, Unit.NONE, 0.1, 3.0, 1.15, 1.15),
	BODY_FALLOFF("body.falloff", Section.FLAMES, Kind.NUMBER, Unit.NONE, 0.2, 6.0, 1.5, 1.5),
	BODY_RAMP("body.ramp", Section.FLAMES, Kind.NUMBER, Unit.TIMES, 0.5, 20.0, 3.0, 3.0),

	FLICKER_STRENGTH("flicker.strength", Section.MOTION, Kind.NUMBER, Unit.NONE, 0.0, 4.0, 1.3, 1.3),
	FLICKER_BASE("flicker.base", Section.MOTION, Kind.NUMBER, Unit.NONE, 0.0, 2.0, 0.2, 0.2),
	MOTION_SPEED("motion.speed", Section.MOTION, Kind.NUMBER, Unit.TIMES, 0.0, 5.0, 1.0, 1.0),
	MOTION_RISE("motion.rise", Section.MOTION, Kind.NUMBER, Unit.ROWS_PER_SECOND, 0.0, 60.0, FireField.RISE, FireField.RISE),
	SWAY_WIDTH("sway.width", Section.MOTION, Kind.NUMBER, Unit.COLUMNS, 0.0, 6.0, 0.6, 0.6),
	SWAY_SPEED("sway.speed", Section.MOTION, Kind.NUMBER, Unit.RADIANS_PER_SECOND, 0.0, 12.0, 1.7, 1.7),
	SWAY_ROWS("sway.rows", Section.MOTION, Kind.NUMBER, Unit.RADIANS_PER_ROW, 0.0, 3.0, 0.35, 0.35),
	NOISE_LARGE("noise.large", Section.MOTION, Kind.NUMBER, Unit.NONE, 0.0, 1.0, 0.5, 0.5),
	NOISE_MEDIUM("noise.medium", Section.MOTION, Kind.NUMBER, Unit.NONE, 0.0, 1.0, 0.3, 0.3),
	NOISE_FINE("noise.fine", Section.MOTION, Kind.NUMBER, Unit.NONE, 0.0, 1.0, 0.2, 0.2),
	NOISE_SCALE("noise.scale", Section.MOTION, Kind.NUMBER, Unit.COLUMNS, 0.25, 16.0, 2.0, 2.0),
	NOISE_ASPECT("noise.aspect", Section.MOTION, Kind.NUMBER, Unit.TIMES, 0.25, 8.0, 2.0, 2.0),

	EMBER_ROWS("ember.rows", Section.EMBERS, Kind.INTEGER, Unit.ROWS, 0, 8, FireField.EMBER_ROWS, FireField.EMBER_ROWS),
	EMBER_GLOW("ember.glow", Section.EMBERS, Kind.NUMBER, Unit.NONE, 0.0, 1.0, 0.12, 0.12),
	EMBER_FLICKER("ember.flicker", Section.EMBERS, Kind.NUMBER, Unit.NONE, 0.0, 1.0, 0.08, 0.08),

	GROWTH_FROM("growth.from", Section.GROWTH, Kind.NUMBER, Unit.SHARE, 0.0, 0.95, 0.5, 0.0),
	GROWTH_MIN_JUDGED("growth.minJudged", Section.GROWTH, Kind.INTEGER, Unit.NOTES, 0, 64, 4, 0),
	GROWTH_FULL("growth.full", Section.GROWTH, Kind.INTEGER, Unit.NOTES, 1, 128, 16, 16),
	GROWTH_RISE("growth.rise", Section.GROWTH, Kind.NUMBER, Unit.HEIGHTS_PER_SECOND, 0.05, 20.0, 2.0, 2.0),
	GROWTH_FALL("growth.fall", Section.GROWTH, Kind.NUMBER, Unit.HEIGHTS_PER_SECOND, 0.02, 20.0, 1.0 / 3.0, 1.0),
	GROWTH_MAX_STEP("growth.maxStep", Section.GROWTH, Kind.NUMBER, Unit.MILLISECONDS, 5.0, 500.0, 50.0, 50.0),

	SPARKS_BASE("sparks.base", Section.SPARKS, Kind.INTEGER, Unit.SPARKS, 0, 200, 20, 20),
	SPARKS_PER_STREAK("sparks.perStreak", Section.SPARKS, Kind.NUMBER, Unit.SPARKS, 0.0, 50.0, 2.5, 2.5),
	SPARKS_STREAK_CAP("sparks.streakCap", Section.SPARKS, Kind.INTEGER, Unit.NOTES, 0, 128, 12, 12),
	SPARKS_MOST("sparks.most", Section.SPARKS, Kind.INTEGER, Unit.SPARKS, 0, 1000, FlameDrawing.MOST_SPARKS, FlameDrawing.MOST_SPARKS),
	SPARKS_LIFE("sparks.life", Section.SPARKS, Kind.NUMBER, Unit.SECONDS, 0.1, 5.0, FlameDrawing.SPARK_LIFE / 1e9, FlameDrawing.SPARK_LIFE / 1e9),
	SPARKS_RISE("sparks.rise", Section.SPARKS, Kind.NUMBER, Unit.ROWS, 0.0, 200.0, 10.0, 10.0),
	SPARKS_RISE_SPREAD("sparks.riseSpread", Section.SPARKS, Kind.NUMBER, Unit.ROWS, 0.0, 200.0, 16.0, 16.0),
	SPARKS_DRIFT("sparks.drift", Section.SPARKS, Kind.NUMBER, Unit.COLUMNS, 0.0, 100.0, 10.0, 10.0),
	SPARKS_ZONE("sparks.zone", Section.SPARKS, Kind.NUMBER, Unit.SHARE, 0.0, 0.5, 0.25, 0.25),
	SPARKS_START("sparks.start", Section.SPARKS, Kind.NUMBER, Unit.SHARE, 0.0, 1.0, 1.0, 0.0),
	SPARKS_FADE("sparks.fade", Section.SPARKS, Kind.NUMBER, Unit.STEPS, 0.0, 64.0, 8.0, 8.0),

	COLOUR_STEPS("colour.steps", Section.COLOURS, Kind.INTEGER, Unit.STEPS, 4, 64, FireColours.STEPS, FireColours.STEPS),
	COLOUR_1("colour.1", Section.COLOURS, Kind.COLOUR, Unit.NONE, 0, 0xFFFFFF, FireColours.scale(0), FireColours.scale(0)),
	COLOUR_2("colour.2", Section.COLOURS, Kind.COLOUR, Unit.NONE, 0, 0xFFFFFF, FireColours.scale(1), FireColours.scale(1)),
	COLOUR_3("colour.3", Section.COLOURS, Kind.COLOUR, Unit.NONE, 0, 0xFFFFFF, FireColours.scale(2), FireColours.scale(2)),
	COLOUR_4("colour.4", Section.COLOURS, Kind.COLOUR, Unit.NONE, 0, 0xFFFFFF, FireColours.scale(3), FireColours.scale(3)),
	COLOUR_5("colour.5", Section.COLOURS, Kind.COLOUR, Unit.NONE, 0, 0xFFFFFF, FireColours.scale(4), FireColours.scale(4));

	/** The groups of rows of the Advanced tab, in their order. */
	public enum Section {
		LOOK, FLAMES, MOTION, EMBERS, GROWTH, SPARKS, COLOURS;

		/** The key of its title. */
		public String labelKey() {
			return "practice.fire.section." + name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/** How a value is held and shown. */
	public enum Kind {
		NUMBER, INTEGER, COLOUR
	}

	/** The unit a value is shown in; a share is shown as a percent. */
	public enum Unit {
		NONE, SHARE, TIMES, PIXELS, COLUMNS, ROWS, ROWS_PER_SECOND, RADIANS_PER_SECOND, RADIANS_PER_ROW, NOTES, HEIGHTS_PER_SECOND, MILLISECONDS, SPARKS,
		SECONDS, STEPS;

		/** The key of its short text, or null when it has none. */
		public String labelKey() {
			return this == NONE ? null : "practice.fire.unit." + name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	private final String key;
	private final Section section;
	private final Kind kind;
	private final Unit unit;
	private final double min;
	private final double max;
	private final double arcadeDefault;
	private final double scoreDefault;

	FireParameter(String key, Section section, Kind kind, Unit unit, double min, double max, double arcadeDefault, double scoreDefault) {
		this.key = key;
		this.section = section;
		this.kind = kind;
		this.unit = unit;
		this.min = min;
		this.max = max;
		this.arcadeDefault = arcadeDefault;
		this.scoreDefault = scoreDefault;
	}

	public String key() {
		return this.key;
	}

	public Section section() {
		return this.section;
	}

	public Kind kind() {
		return this.kind;
	}

	public Unit unit() {
		return this.unit;
	}

	public double min() {
		return this.min;
	}

	public double max() {
		return this.max;
	}

	public double arcadeDefault() {
		return this.arcadeDefault;
	}

	public double scoreDefault() {
		return this.scoreDefault;
	}

	public double defaultFor(FireTuning.Group group) {
		return group == FireTuning.Group.SCORE ? this.scoreDefault : this.arcadeDefault;
	}

	/** Shown in Arcade's group: every value but the streak of full height, which only the score fire has. */
	public boolean inArcade() {
		return this != GROWTH_FULL;
	}

	/** Shown in the score fire's group: every value. */
	public boolean inScore() {
		return true;
	}

	public boolean in(FireTuning.Group group) {
		return group == FireTuning.Group.SCORE ? inScore() : inArcade();
	}

	public String labelKey() {
		return "practice.fire." + this.key;
	}

	public String tooltipKey() {
		return "practice.fire." + this.key + ".tip";
	}

	/** The parameter of a key, or null. */
	public static FireParameter of(String key) {
		for (FireParameter parameter : values()) {
			if (parameter.key.equals(key)) {
				return parameter;
			}
		}
		return null;
	}
}
