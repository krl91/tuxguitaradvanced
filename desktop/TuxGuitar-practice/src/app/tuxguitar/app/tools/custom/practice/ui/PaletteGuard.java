package app.tuxguitar.app.tools.custom.practice.ui;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Supplier;

import app.tuxguitar.app.tools.custom.practice.model.PracticeEvent;
import app.tuxguitar.ui.resource.UIRectangle;
import app.tuxguitar.ui.widget.UIWindow;
import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.TGSynchronizer;

/**
 * Keeps the practice palette off the note to play during a Wait or Follow run (spec 017): at each change of the framed note and every
 * {@link PaletteDodge#CHECK_MILLIS} while a run goes, it reads the boxes of the score ({@link ScoreBoxes}), asks {@link PaletteDodge} and moves the
 * palette. Never used by Arcade. UI thread only, except the timer, which only posts to the synchronizer.
 */
final class PaletteGuard {

	private final TGContext context;
	private final PracticePalette palette;
	private final Supplier<UIWindow> mainWindow;
	private final ScoreBoxes boxes;
	private PaletteDodge dodge;
	private Timer timer;
	private PracticeEvent current;
	private List<PracticeEvent> events;
	private boolean paused;

	PaletteGuard(TGContext context, PracticePalette palette, Supplier<UIWindow> mainWindow) {
		this.context = context;
		this.palette = palette;
		this.mainWindow = mainWindow;
		this.boxes = new ScoreBoxes(context);
	}

	/**
	 * The framed note of the run now (UI thread).
	 *
	 * @param current the event to play, or null at the end of the run (the palette then stays where it is)
	 * @param events the events of the run, in order, to find the next one
	 * @param paused true while the run is paused: only the framed note counts, not the next one
	 */
	void show(PracticeEvent current, List<PracticeEvent> events, boolean paused) {
		this.current = current;
		this.events = events;
		this.paused = paused;
		if( current == null ) {
			cancelTimer();
			return;
		}
		if( this.dodge == null ) {
			this.dodge = new PaletteDodge();
		}
		if( this.timer == null ) {
			this.timer = new Timer("practice-palette-guard", true);
			this.timer.schedule(new TimerTask() {
				public void run() {
					TGSynchronizer.getInstance(PaletteGuard.this.context).executeLater(PaletteGuard.this::check);
				}
			}, PaletteDodge.CHECK_MILLIS, PaletteDodge.CHECK_MILLIS);
		}
		check();
	}

	/** The run ended or the palette closed: nothing more is moved, and the next run starts afresh. */
	void stop() {
		cancelTimer();
		this.current = null;
		this.events = null;
		this.dodge = null;
	}

	private void check() {
		if( this.current == null || this.dodge == null || !this.palette.isOpen() ) {
			return;
		}
		PaletteDodge.Box area = this.boxes.area();
		PaletteDodge.Box framed = this.boxes.of(this.current, area);
		PaletteDodge.Box next = (this.paused ? null : this.boxes.of(next(), area));
		PaletteDodge.Decision decision = this.dodge.decide(this.palette.bounds(), window(), area, framed, next, this.current);
		if( decision.isMove() ) {
			this.palette.moveTo((float) decision.x(), (float) decision.y());
		}
	}

	private PracticeEvent next() {
		if( this.events == null ) {
			return null;
		}
		int index = this.events.indexOf(this.current);
		return (index >= 0 && index + 1 < this.events.size() ? this.events.get(index + 1) : null);
	}

	private PaletteDodge.Box window() {
		UIWindow window = this.mainWindow.get();
		if( window == null || window.isDisposed() ) {
			return null;
		}
		UIRectangle bounds = window.getBounds();
		return new PaletteDodge.Box(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight());
	}

	private void cancelTimer() {
		if( this.timer != null ) {
			this.timer.cancel();
			this.timer = null;
		}
	}
}
