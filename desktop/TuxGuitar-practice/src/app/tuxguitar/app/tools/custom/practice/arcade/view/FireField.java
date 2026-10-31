package app.tuxguitar.app.tools.custom.practice.arcade.view;

/**
 * The field of big pixels of the demo fire (spec 018, research R2, R3, R6): square cells along the foot of the picture, the two bottom rows of embers and
 * above them, in each column, the rows of the flame. The height of the flame of a column is that of spec 009, {@code level x factor x 55 % x weight}, with
 * the weight taken at the outer edge of the column so that the outermost columns reach the 55 % exactly; it is not rounded to whole cells, so it follows
 * the level smoothly: the top cell of a column is cut to it. The height never depends on the clock, so the lit cells are the same from one picture to the
 * next; the clock only moves the heat inside them, as the classic fire does: a value noise scrolled upwards with a slight sway, the heat falling from the
 * base to the tip. No stored grid and no random source: the same inputs give the same field. Pure.
 *
 * <p>Every value that shapes it comes from a {@link FireTuning} (spec 019); the constants below are the defaults of Arcade's fire.
 */
final class FireField {

	/** About this many columns across the picture at the full tier; the balanced tier has cells twice as big. */
	static final double COLUMNS = 192.0;

	/** The smallest cell, in screen pixels. */
	static final double SMALLEST = 4.0;

	/** The rows of embers at the foot of the picture, lit even at level 0. */
	static final int EMBER_ROWS = 2;

	/** How fast the heat rises, in rows per second. */
	static final double RISE = 9.0;

	private static final int SEED = 0x2F1A9;

	private final FireTuning tuning;
	private final double cell;
	private final int columns;
	private final double left;
	private final double bottom;
	private final double[] heights;
	private final double level;
	private final double seconds;
	private final int emberRows;
	private final int steps;

	FireField(Viewport viewport, QualityTier tier, FlameState flames) {
		this(viewport, tier, flames, flames.tuning());
	}

	FireField(Viewport viewport, QualityTier tier, FlameState flames, FireTuning tuning) {
		this.tuning = tuning;
		this.cell = cell(viewport, tier, tuning);
		this.columns = Math.max(1, (int) Math.floor(viewport.width() / this.cell));
		this.left = (viewport.width() - this.columns * this.cell) / 2.0;
		this.bottom = viewport.height();
		this.level = flames.level();
		this.seconds = flames.motion() ? flames.flameClockNanos() / 1e9 * tuning.value(FireParameter.MOTION_SPEED) : 0.0;
		this.emberRows = tuning.integer(FireParameter.EMBER_ROWS);
		this.steps = tuning.integer(FireParameter.COLOUR_STEPS);
		double factor = flames.setting() == app.tuxguitar.app.tools.custom.practice.session.FlameSetting.DISCREET ? tuning.value(FireParameter.DISCREET_SHARE) : 1.0;
		double tallest = tuning.value(FireParameter.HEIGHT_CORNERS) * viewport.height();
		this.heights = new double[this.columns];
		for (int col = 0; col < this.columns; col++) {
			this.heights[col] = this.level * factor * tallest * weight(col);
		}
	}

	/** The size of a cell for this picture and tier. */
	static double cell(Viewport viewport, QualityTier tier) {
		return cell(viewport, tier, FireTuning.ARCADE_DEFAULTS);
	}

	/** The size of a cell for this picture, tier and tuning. */
	static double cell(Viewport viewport, QualityTier tier, FireTuning tuning) {
		double cell = Math.max(tuning.value(FireParameter.CELL_SMALLEST), Math.round(viewport.width() / tuning.value(FireParameter.CELL_COLUMNS)));
		return tier == QualityTier.FULL ? cell : 2.0 * cell;
	}

	double cell() {
		return this.cell;
	}

	int columns() {
		return this.columns;
	}

	/** The rows of embers of this field. */
	int emberRows() {
		return this.emberRows;
	}

	/** The height of the flame of a column above the ember rows, in pixels. */
	double height(int col) {
		return this.heights[col];
	}

	/** The rows of flame above the ember rows in this column, the top one maybe cut. */
	int tongueRows(int col) {
		return (int) Math.ceil(this.heights[col] / this.cell - 1e-9);
	}

	/** The weight of spec 009 at the outer edge of a column: 1 for the outermost ones. */
	double weight(int col) {
		return FlameDrawing.weight(2 * col < this.columns ? col / (double) this.columns : (col + 1) / (double) this.columns, this.tuning);
	}

	/** The left edge of a column. */
	double left(int col) {
		return this.left + col * this.cell;
	}

	/** The top edge of a row, row 0 being the bottom one. */
	double top(int row) {
		return this.bottom - (row + 1) * this.cell;
	}

	/** The height of a cell: a whole cell, but the top cell of a flame is cut to its height. */
	double cellHeight(int col, int row) {
		int k = row - this.emberRows;
		int rows = tongueRows(col);
		return k >= 0 && k == rows - 1 ? this.heights[col] - (rows - 1) * this.cell : this.cell;
	}

	/** The heat step of a cell, 1 to the hottest step where it is lit, 0 where it is cold. */
	int heat(int col, int row) {
		if (col < 0 || col >= this.columns || row < 0) {
			return 0;
		}
		double weight = weight(col);
		int rows = tongueRows(col);
		double exact = this.heights[col] / this.cell;
		double heat;
		if (row < this.emberRows) {
			double ember = this.tuning.value(FireParameter.EMBER_GLOW) + this.tuning.value(FireParameter.EMBER_FLICKER) * noise(col, row);
			double up = Math.min(1.0, this.tuning.value(FireParameter.BODY_RAMP) * this.level);
			heat = weight * (ember + (1.0 - ember) * up);
		} else if (row < this.emberRows + rows) {
			int k = row - this.emberRows;
			if (k == 0) {
				heat = weight;
			} else {
				double u = Math.min(1.0, (k + 0.5) / exact);
				heat = weight * clamp(this.tuning.value(FireParameter.BODY_HEAT) * (1.0 - Math.pow(u, this.tuning.value(FireParameter.BODY_FALLOFF)))
						+ this.tuning.value(FireParameter.FLICKER_STRENGTH) * (noise(col, k) - 0.5) * (this.tuning.value(FireParameter.FLICKER_BASE) + u));
			}
		} else {
			return 0;
		}
		return (int) Math.max(1, Math.min(this.steps - 1, Math.round(heat * (this.steps - 1))));
	}

	/** Three octaves of value noise at a cell, taller than wide (rising tongues), scrolled upwards by the clock with a slight sway: 0 to 1. */
	private double noise(int col, int row) {
		double x = col + this.tuning.value(FireParameter.SWAY_WIDTH)
				* Math.sin(this.seconds * this.tuning.value(FireParameter.SWAY_SPEED) + row * this.tuning.value(FireParameter.SWAY_ROWS));
		double y = row - this.seconds * this.tuning.value(FireParameter.MOTION_RISE);
		double scale = this.tuning.value(FireParameter.NOISE_SCALE);
		double tall = this.tuning.value(FireParameter.NOISE_ASPECT) * scale;
		return this.tuning.value(FireParameter.NOISE_LARGE) * value(x / scale, y / tall)
				+ this.tuning.value(FireParameter.NOISE_MEDIUM) * value(x / (scale / 2.0), y / (tall / 2.0))
				+ this.tuning.value(FireParameter.NOISE_FINE) * value(x / (scale / 4.0), y / (tall / 4.0));
	}

	private static double value(double x, double y) {
		int x0 = (int) Math.floor(x);
		int y0 = (int) Math.floor(y);
		double fx = smooth(x - x0);
		double fy = smooth(y - y0);
		double top = lerp(hash(x0, y0), hash(x0 + 1, y0), fx);
		double low = lerp(hash(x0, y0 + 1), hash(x0 + 1, y0 + 1), fx);
		return lerp(top, low, fy);
	}

	/** An integer hash of a lattice point, 0 to 1. */
	private static double hash(int x, int y) {
		int h = x * 374761393 + y * 668265263 + SEED;
		h = (h ^ (h >>> 13)) * 1274126177;
		h ^= h >>> 16;
		return (h & 0xFFFFFF) / (double) 0x1000000;
	}

	private static double smooth(double f) {
		return f * f * (3.0 - 2.0 * f);
	}

	private static double lerp(double a, double b, double f) {
		return a + (b - a) * f;
	}

	private static double clamp(double v) {
		return Math.max(0.0, Math.min(1.0, v));
	}
}
