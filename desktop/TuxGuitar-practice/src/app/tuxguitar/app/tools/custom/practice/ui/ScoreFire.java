package app.tuxguitar.app.tools.custom.practice.ui;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
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
import app.tuxguitar.app.tools.custom.practice.arcade.view.Viewport;
import app.tuxguitar.app.tools.custom.practice.model.PracticeEvent;
import app.tuxguitar.app.tools.custom.practice.session.FlameSetting;
import app.tuxguitar.app.view.component.tab.TGControl;
import app.tuxguitar.app.view.component.tab.TGExternalNoteFramePainter;
import app.tuxguitar.app.view.component.tab.Tablature;
import app.tuxguitar.app.view.component.tab.TablatureEditor;
import app.tuxguitar.app.view.component.tabfolder.TGTabFolder;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.event.UIPaintEvent;
import app.tuxguitar.ui.event.UIPaintListener;
import app.tuxguitar.ui.resource.UIPainter;
import app.tuxguitar.ui.resource.UIPosition;
import app.tuxguitar.ui.resource.UIRectangle;
import app.tuxguitar.ui.widget.UICanvas;
import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.TGSynchronizer;

/**
 * The demo fire at the bottom of the score in Wait and Follow (spec 019 FR-001, FR-003, FR-009, FR-010, contracts/score-overlay.md, data model
 * "ScoreFire"). A second paint listener of the score's canvas, painted after the score on the same painter, so that it never touches the score's own
 * drawing or its buffer. It grows with the streak of right notes, holds still in a pause, and asks for about 30 pictures a second while it moves and
 * none when it is still. Off, it adds no listener, no timer and no redraw. It reads the run, never writes it (FR-008). UI thread only, except the timer,
 * which only posts its ticks.
 */
public final class ScoreFire {

	/** The score the fire is drawn on: its canvas, the boxes the fire must not cover, and its redraws. */
	public interface Score {

		/** The canvas of the score shown now, or null when there is none. */
		UICanvas canvas();

		/** The boxes {x1, y1, x2, y2} of the canvas the fire must not cover, asked at each picture: the framed note, the next note and the palette. */
		List<double[]> guardBoxes(PracticeEvent current, PracticeEvent next, PaletteDodge.Box palette);

		/** Asks for a picture of the score; {@code overlayOnly} when only the fire changed, so that the score need not be drawn again. */
		void redraw(boolean overlayOnly);
	}

	/** The timer of the pictures: it calls the tick on the UI thread about every {@link #FRAME_MILLIS}, between start and stop. */
	public interface Ticker {
		void start(Runnable tick);

		void stop();
	}

	/** About 30 pictures a second. */
	static final long FRAME_MILLIS = 33L;

	/** The fire's own share of a 33 ms frame (plan: 4 ms goal, SC-006): above 80 % of this on average the quality steps down. */
	static final long BUDGET_NANOS = 8_000_000L;

	private final UIFactory factory;
	private final Score score;
	private final Ticker ticker;
	private final LongSupplier wall;
	private final LongSupplier cost;
	private final UIPaintListener listener = this::onPaint;

	private FlameSetting setting = FlameSetting.OFF;
	private FireTuning tuning = FireTuning.SCORE_DEFAULTS;
	private boolean motion;
	private FlameLevel level;
	private QualityController quality = new QualityController(BUDGET_NANOS);
	private UiPainterRenderer renderer;
	private UICanvas attached;
	private boolean ticking;
	private boolean paused;
	private long clock;
	private long last = Long.MIN_VALUE;
	private int streak;
	private int judged;
	private final List<FlameState.Burst> bursts = new ArrayList<FlameState.Burst>();
	private PracticeEvent current;
	private PracticeEvent next;
	private PaletteDodge.Box palette;
	private int lastPainted;

	/**
	 * @param factory the toolkit, for the colours of the renderer; null paints nothing (tests of the run alone)
	 * @param wall the wall clock of the pictures, in nanoseconds
	 * @param cost the clock the painting time is measured with, in nanoseconds
	 */
	public ScoreFire(UIFactory factory, Score score, Ticker ticker, LongSupplier wall, LongSupplier cost) {
		this.factory = factory;
		this.score = score;
		this.ticker = ticker;
		this.wall = wall;
		this.cost = cost;
	}

	/** The fire on the score of the application, with a timer through the synchronizer of the context. */
	public static ScoreFire onTheScore(UIFactory factory, TGContext context) {
		return new ScoreFire(factory, score(context), ticker(context), System::nanoTime, System::nanoTime);
	}

	/**
	 * A Wait or Follow run starts with these settings, read when it starts.
	 *
	 * @param motion false when the system asks to reduce motion: the fire then does not flicker and throws no spark, and only its height follows the play
	 */
	public void start(FlameSetting setting, FireTuning tuning, boolean motion) {
		stop();
		this.setting = (setting != null ? setting : FlameSetting.OFF);
		if (this.setting == FlameSetting.OFF) {
			return;
		}
		this.tuning = (tuning != null ? tuning : FireTuning.SCORE_DEFAULTS);
		this.motion = motion;
		this.level = new FlameLevel(this.tuning);
		this.quality = new QualityController(BUDGET_NANOS);
		this.clock = 0L;
		this.last = Long.MIN_VALUE;
		this.paused = false;
		this.streak = 0;
		this.judged = 0;
		this.bursts.clear();
		attach();
		advance();
		startTicking();
		this.score.redraw(true);
	}

	/**
	 * What the run shows now, from each picture of the run (UI thread).
	 *
	 * @param newBursts the streak of each right note since the last update
	 * @param paused true while the run is paused: the fire holds still
	 * @param current the event to play, or null; next the one after it, or null; palette the palette's box on the screen, or null
	 */
	public void update(int streak, int judged, List<Integer> newBursts, boolean paused, PracticeEvent current, PracticeEvent next, PaletteDodge.Box palette) {
		if (this.setting == FlameSetting.OFF) {
			return;
		}
		this.current = current;
		this.next = next;
		this.palette = palette;
		setPaused(paused);
		advance();
		this.streak = streak;
		this.judged = judged;
		if (this.motion && newBursts != null) {
			for (Integer burst : newBursts) {
				this.bursts.add(new FlameState.Burst(this.clock, burst));
			}
		}
		attach();
		if (moving()) {
			startTicking();
		}
	}

	/** One tick of the timer: the fire moves on and asks for a picture, or the timer stops when the fire is still. */
	public void tick() {
		if (this.setting == FlameSetting.OFF) {
			return;
		}
		advance();
		attach();
		boolean moving = moving();
		this.score.redraw(true);
		if (!moving) {
			stopTicking();
		}
	}

	/** The run ended: the listener goes and the score is drawn once more without the fire. */
	public void stop() {
		stopTicking();
		boolean was = detach();
		this.setting = FlameSetting.OFF;
		this.bursts.clear();
		this.current = null;
		this.next = null;
		this.palette = null;
		if (this.renderer != null) {
			this.renderer.dispose();
			this.renderer = null;
		}
		if (was) {
			this.score.redraw(true);
		}
	}

	public boolean isAttached() {
		return this.attached != null;
	}

	/** The quality tier of the next picture. */
	public QualityTier tier() {
		return this.quality.tier();
	}

	/** The level of the fire now, 0 to 1. */
	public double level() {
		return this.level != null && this.setting != FlameSetting.OFF ? this.level.level() : 0.0;
	}

	/** How many items the last picture painted. */
	int lastPainted() {
		return this.lastPainted;
	}

	/** Paints the fire of now on a painter the size of the score's canvas, and feeds its cost to the quality controller. */
	void paint(UIPainter painter, float width, float height) {
		long start = this.cost.getAsLong();
		FlameState flames = flames();
		QualityTier tier = this.quality.tier();
		Scene scene = ScoreFireScene.scene(new Viewport(width, height), tier, flames, this.score.guardBoxes(this.current, this.next, this.palette));
		this.lastPainted = scene.items().size();
		if (this.lastPainted > 0 && this.factory != null) {
			if (this.renderer == null) {
				this.renderer = new UiPainterRenderer(this.factory, null);
			}
			this.renderer.target(painter);
			this.renderer.render(scene, tier);
		}
		this.quality.frame(this.cost.getAsLong() - start);
	}

	private void onPaint(UIPaintEvent event) {
		UICanvas canvas = this.attached;
		if (canvas == null || canvas.isDisposed() || canvas != this.score.canvas() || this.setting == FlameSetting.OFF) {
			return;
		}
		UIRectangle bounds = canvas.getBounds();
		paint(event.getPainter(), bounds.getWidth(), bounds.getHeight());
	}

	/** What the next picture draws: the setting, the level, the fire clock and the live bursts of sparks (spec 019 FR-006); null when off. */
	FlameState flames() {
		if (this.setting == FlameSetting.OFF || this.level == null) {
			return null;
		}
		return new FlameState(this.setting, this.level.level(), this.clock, this.motion, this.bursts, this.tuning);
	}

	/** The fire moves on to the wall clock of now: its clock while the run goes, its level towards the streak, its sparks dying out. */
	private void advance() {
		long now = this.wall.getAsLong();
		if (this.last != Long.MIN_VALUE && !this.paused) {
			this.clock += Math.max(0L, now - this.last);
		}
		this.last = now;
		if (!this.paused) {
			this.level.update(target(), this.judged, now);
		}
		long life = Math.round(this.tuning.value(FireParameter.SPARKS_LIFE) * 1e9);
		for (Iterator<FlameState.Burst> burst = this.bursts.iterator(); burst.hasNext();) {
			if (this.clock - burst.next().clockNanos() >= life) {
				burst.remove();
			}
		}
	}

	/** The height the streak asks for: full at {@code growth.full} right notes in a row. */
	private double target() {
		return Math.min(1.0, this.streak / (double) Math.max(1, this.tuning.integer(FireParameter.GROWTH_FULL)));
	}

	private void setPaused(boolean paused) {
		if (paused == this.paused) {
			return;
		}
		long now = this.wall.getAsLong();
		if (paused) {
			advance();
			this.level.hold(now);
			this.paused = true;
		} else {
			this.paused = false;
			this.last = now;
			this.level.resume(now);
		}
	}

	private boolean moving() {
		return !this.paused && (this.motion || !this.bursts.isEmpty() || Math.abs(this.level.level() - target()) > 1e-9);
	}

	/** The listener follows the canvas of the score shown now. */
	private void attach() {
		UICanvas canvas = this.score.canvas();
		if (canvas == this.attached) {
			return;
		}
		detach();
		if (canvas != null && !canvas.isDisposed()) {
			canvas.addPaintListener(this.listener);
			this.attached = canvas;
		}
	}

	private boolean detach() {
		UICanvas canvas = this.attached;
		this.attached = null;
		if (canvas != null && !canvas.isDisposed()) {
			canvas.removePaintListener(this.listener);
			return true;
		}
		return canvas != null;
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

	// ---------------------------------------------------------------- the application's score and timer

	/** The score of the selected tab: its canvas, the frames of the notes and the palette (as {@link ScoreBoxes}), and its redraws. */
	static Score score(final TGContext context) {
		return new Score() {
			public UICanvas canvas() {
				TGControl control = TGTabFolder.getInstance(context).findSelectedControl();
				return (control != null && !control.isDisposed() ? control.getCanvas() : null);
			}

			public List<double[]> guardBoxes(PracticeEvent current, PracticeEvent next, PaletteDodge.Box palette) {
				UICanvas canvas = canvas();
				UIPosition origin = (canvas != null ? canvas.toDisplay(new UIPosition(0f, 0f)) : null);
				final Tablature tablature = TablatureEditor.getInstance(context).getTablature();
				ScoreGuardBoxes.Frames frames = event -> {
					UIRectangle frame = TGExternalNoteFramePainter.frameBounds(tablature.getViewLayout(), ((PracticeEvent) event).getNotes(), tablature.getTrackSelection());
					return frame != null ? new double[] { frame.getX(), frame.getY(), frame.getWidth(), frame.getHeight() } : null;
				};
				// without the screen place of the canvas, the palette cannot be placed on it
				return ScoreGuardBoxes.of(frames, current, next, origin != null ? palette : null, origin != null ? origin.getX() : 0.0, origin != null ? origin.getY() : 0.0);
			}

			public void redraw(boolean overlayOnly) {
				TGControl control = TGTabFolder.getInstance(context).findSelectedControl();
				if (control == null || control.isDisposed()) {
					return;
				}
				if (overlayOnly) {
					control.redrawOverlay();
				} else {
					control.redraw();
				}
			}
		};
	}

	/** A timer thread that posts each tick to the UI thread, never more than one waiting. */
	static Ticker ticker(final TGContext context) {
		return new Ticker() {
			private Timer timer;

			public void start(final Runnable tick) {
				stop();
				final AtomicBoolean waiting = new AtomicBoolean();
				this.timer = new Timer("practice-score-fire", true);
				this.timer.schedule(new TimerTask() {
					public void run() {
						if (waiting.compareAndSet(false, true)) {
							TGSynchronizer.getInstance(context).executeLater(() -> {
								waiting.set(false);
								tick.run();
							});
						}
					}
				}, FRAME_MILLIS, FRAME_MILLIS);
			}

			public void stop() {
				if (this.timer != null) {
					this.timer.cancel();
					this.timer = null;
				}
			}
		};
	}
}
