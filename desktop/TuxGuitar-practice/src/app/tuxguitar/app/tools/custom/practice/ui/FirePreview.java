package app.tuxguitar.app.tools.custom.practice.ui;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.LongSupplier;

import app.tuxguitar.app.tools.custom.practice.arcade.ui.FlameLevel;
import app.tuxguitar.app.tools.custom.practice.arcade.ui.UiPainterRenderer;
import app.tuxguitar.app.tools.custom.practice.arcade.view.FireParameter;
import app.tuxguitar.app.tools.custom.practice.arcade.view.FireTuning;
import app.tuxguitar.app.tools.custom.practice.arcade.view.FlameState;
import app.tuxguitar.app.tools.custom.practice.arcade.view.QualityController;
import app.tuxguitar.app.tools.custom.practice.arcade.view.QualityTier;
import app.tuxguitar.app.tools.custom.practice.arcade.view.Scene;
import app.tuxguitar.app.tools.custom.practice.arcade.view.ScoreFireScene;
import app.tuxguitar.app.tools.custom.practice.arcade.view.Theme;
import app.tuxguitar.app.tools.custom.practice.arcade.view.Viewport;
import app.tuxguitar.app.tools.custom.practice.session.FlameSetting;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.event.UIPaintEvent;
import app.tuxguitar.ui.resource.UIColor;
import app.tuxguitar.ui.resource.UIPainter;
import app.tuxguitar.ui.resource.UIRectangle;
import app.tuxguitar.ui.widget.UICanvas;
import app.tuxguitar.ui.widget.UIContainer;

/**
 * The animated preview of one fire in the Advanced tab (spec 019 FR-020, data model "FirePreview"): the same scene code as the real fire, the score fire's
 * from {@link ScoreFireScene#scene} and Arcade's from {@link ScoreFireScene#arcadePreview}, on a small dark canvas. It follows each new tuning at its next
 * picture. Its growth slider gives the streak for the score fire (0 to 1.5 × {@code growth.full}) and the share of the maximum for Arcade; its *Sparks*
 * button throws one burst at the slider's streak (for Arcade, the share of {@code sparks.streakCap}). It asks for about 30 pictures a second only while it
 * is shown and the system lets things move; with Reduce motion it is drawn still, without sparks, and only its height follows the slider. UI thread only,
 * except the timer, which only posts its ticks.
 */
public final class FirePreview {

	/** The width and height of the canvas, about 360×120 (contracts/advanced-tab.md). */
	static final float WIDTH = 360f;
	static final float HEIGHT = 120f;

	/** The score fire's slider reaches past the full height: 1.5 × {@code growth.full} right notes. */
	static final double SCORE_REACH = 1.5;

	private final UIFactory factory;
	private final ScoreFire.Ticker ticker;
	private final LongSupplier wall;
	private final LongSupplier cost;
	private final List<FlameState.Burst> bursts = new ArrayList<FlameState.Burst>();

	private FireTuning tuning;
	private FlameLevel level;
	private QualityController quality = new QualityController(ScoreFire.BUDGET_NANOS);
	private UICanvas canvas;
	private UIColor background;
	private UiPainterRenderer renderer;
	private boolean motion = true;
	private boolean shown;
	private boolean ticking;
	private double growth;
	private long clock;
	private long last = Long.MIN_VALUE;

	/**
	 * @param factory the toolkit, for the canvas and its colours; null for a preview with no canvas (tests)
	 * @param tuning the tuning of its group, which says which fire it shows
	 */
	public FirePreview(UIFactory factory, FireTuning tuning, ScoreFire.Ticker ticker, LongSupplier wall, LongSupplier cost) {
		this.factory = factory;
		this.ticker = ticker;
		this.wall = wall;
		this.cost = cost;
		this.tuning = (tuning != null ? tuning : FireTuning.ARCADE_DEFAULTS);
		this.level = new FlameLevel(this.tuning);
	}

	/** The canvas of the preview, made once, with the dark background of the Arcade design under the fire. */
	public UICanvas createCanvas(UIContainer parent) {
		this.canvas = this.factory.createCanvas(parent, false);
		int dark = Theme.DARK.background();
		this.background = this.factory.createColor((dark >> 16) & 0xFF, (dark >> 8) & 0xFF, dark & 0xFF);
		this.canvas.setBgColor(this.background);
		this.canvas.addPaintListener(this::onPaint);
		return this.canvas;
	}

	public FireTuning tuning() {
		return this.tuning;
	}

	/** A new tuning, drawn at the next picture. The growth starts again only when a value of the growth changed. */
	public void setTuning(FireTuning tuning) {
		FireTuning chosen = (tuning != null ? tuning : FireTuning.defaults(this.tuning.group()));
		if (!sameGrowth(this.tuning, chosen)) {
			this.level = new FlameLevel(chosen);
			this.last = Long.MIN_VALUE;
		}
		this.tuning = chosen;
		redraw();
	}

	/** The growth slider, 0 to 1. */
	public void setGrowth(double position) {
		this.growth = Math.max(0.0, Math.min(1.0, position));
		if (this.motion && this.shown) {
			startTicking();
		}
		redraw();
	}

	/** The streak the slider stands for: right notes in a row for the score fire, the share of the spark cap for Arcade. */
	public int streak() {
		if (this.tuning.group() == FireTuning.Group.SCORE) {
			return (int) Math.round(this.growth * SCORE_REACH * this.tuning.integer(FireParameter.GROWTH_FULL));
		}
		return (int) Math.round(this.growth * this.tuning.integer(FireParameter.SPARKS_STREAK_CAP));
	}

	/** The share the fire grows towards: the streak over {@code growth.full} for the score fire, the slider itself for Arcade. */
	public double share() {
		if (this.tuning.group() == FireTuning.Group.SCORE) {
			return Math.min(1.0, streak() / (double) Math.max(1, this.tuning.integer(FireParameter.GROWTH_FULL)));
		}
		return this.growth;
	}

	/** One burst of sparks at the slider's streak, thrown now; none with Reduce motion. */
	public void sparks() {
		if (!this.motion) {
			return;
		}
		advance();
		this.bursts.add(new FlameState.Burst(this.clock, streak()));
		if (this.shown) {
			startTicking();
		}
		redraw();
	}

	/** False when the system asks to reduce motion. */
	public void setMotion(boolean motion) {
		this.motion = motion;
		if (!motion) {
			this.bursts.clear();
			stopTicking();
		} else if (this.shown) {
			startTicking();
		}
		redraw();
	}

	/** Shown while its tab is selected and the window is open: it moves only then. */
	public void setShown(boolean shown) {
		this.shown = shown;
		if (shown && this.motion) {
			this.last = Long.MIN_VALUE;
			startTicking();
		} else {
			stopTicking();
		}
		redraw();
	}

	/** One tick of the timer: the fire moves on and the canvas asks for a picture. */
	public void tick() {
		advance();
		redraw();
	}

	public void dispose() {
		stopTicking();
		if (this.renderer != null) {
			this.renderer.dispose();
			this.renderer = null;
		}
		if (this.background != null) {
			this.background.dispose();
			this.background = null;
		}
	}

	/** What the next picture draws. */
	FlameState flames() {
		double shown = this.motion ? this.level.level() : still();
		return new FlameState(FlameSetting.FULL, shown, this.clock, this.motion, this.bursts, this.tuning);
	}

	/** The scene of the next picture, at the tier the painting time allows. */
	Scene scene(Viewport viewport) {
		return scene(viewport, this.quality.tier());
	}

	private Scene scene(Viewport viewport, QualityTier tier) {
		if (this.tuning.group() == FireTuning.Group.SCORE) {
			return ScoreFireScene.scene(viewport, tier, flames(), null);
		}
		return ScoreFireScene.arcadePreview(viewport, tier, flames());
	}

	private void onPaint(UIPaintEvent event) {
		if (this.canvas == null || this.canvas.isDisposed()) {
			return;
		}
		long start = this.cost.getAsLong();
		UIRectangle bounds = this.canvas.getBounds();
		QualityTier tier = this.quality.tier();
		Scene scene = scene(new Viewport(Math.max(1f, bounds.getWidth()), Math.max(1f, bounds.getHeight())), tier);
		if (!scene.items().isEmpty()) {
			if (this.renderer == null) {
				this.renderer = new UiPainterRenderer(this.factory, null);
			}
			UIPainter painter = event.getPainter();
			this.renderer.target(painter);
			this.renderer.render(scene, tier);
		}
		this.quality.frame(this.cost.getAsLong() - start);
	}

	/** The level the fire stands at with Reduce motion: the one it grows to, at once. */
	private double still() {
		double from = this.tuning.value(FireParameter.GROWTH_FROM);
		return Math.max(0.0, Math.min(1.0, (share() - from) / (1.0 - from)));
	}

	/** The clock of the fire moves on with the wall clock while it moves; the level grows towards the share; dead sparks go. */
	private void advance() {
		long now = this.wall.getAsLong();
		if (this.motion) {
			if (this.last != Long.MIN_VALUE) {
				this.clock += Math.max(0L, now - this.last);
			}
			// the preview has no judged notes to wait for: the growth starts at once
			this.level.update(share(), Integer.MAX_VALUE, now);
		}
		this.last = now;
		long life = Math.round(this.tuning.value(FireParameter.SPARKS_LIFE) * 1e9);
		for (Iterator<FlameState.Burst> burst = this.bursts.iterator(); burst.hasNext();) {
			if (this.clock - burst.next().clockNanos() >= life) {
				burst.remove();
			}
		}
	}

	private static boolean sameGrowth(FireTuning a, FireTuning b) {
		for (FireParameter parameter : FireParameter.values()) {
			if (parameter.section() == FireParameter.Section.GROWTH && a.value(parameter) != b.value(parameter)) {
				return false;
			}
		}
		return true;
	}

	private void redraw() {
		if (this.canvas != null && !this.canvas.isDisposed()) {
			this.canvas.redraw();
		}
	}

	private void startTicking() {
		if (!this.ticking) {
			this.ticking = true;
			this.ticker.start(this::tick);
		}
	}

	private void stopTicking() {
		if (this.ticking) {
			this.ticking = false;
			this.ticker.stop();
		}
	}
}
