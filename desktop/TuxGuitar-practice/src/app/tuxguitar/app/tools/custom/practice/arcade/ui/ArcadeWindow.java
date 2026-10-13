package app.tuxguitar.app.tools.custom.practice.arcade.ui;

import app.tuxguitar.app.tools.custom.practice.arcade.view.QualityController;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.event.UIDisposeEvent;
import app.tuxguitar.ui.event.UIDisposeListener;
import app.tuxguitar.ui.event.UISelectionEvent;
import app.tuxguitar.ui.event.UISelectionListener;
import app.tuxguitar.ui.layout.UITableLayout;
import app.tuxguitar.ui.resource.UIRectangle;
import app.tuxguitar.ui.widget.UIButton;
import app.tuxguitar.ui.widget.UILabel;
import app.tuxguitar.ui.widget.UIPanel;
import app.tuxguitar.ui.widget.UIWindow;

/**
 * The window of an Arcade run (spec 003 T053): the canvas with the isometric view (the streak, the speed and the next event are drawn on it, never the
 * score), and under it Restart, Pause/Resume and Stop. Closing the window is stopping the run. It owns the canvas and its timer, and gives them back when it closes.
 * UI thread only.
 */
public final class ArcadeWindow {

	/** What the buttons ask of the run. */
	public interface Actions {
		void pause();

		void resume();

		void stop();

		/** Starts the piece again from its beginning. */
		void restart();
	}

	private final ArcadeTexts texts;
	private final Actions actions;
	private final ArcadeCanvas.UiThread ui;
	private final UIWindow window;
	private final ArcadeCanvas canvas;
	private final UIButton pause;
	private final UILabel state;
	private final UIPanel buttonsPanel;
	private boolean paused;
	private boolean closing;

	public ArcadeWindow(UIFactory factory, UIWindow parent, ArcadeFrameSource source, ArcadeCanvas.UiThread ui, ArcadeTexts texts, Actions actions) {
		this.texts = texts;
		this.actions = actions;
		this.ui = ui;
		this.window = factory.createWindow(parent, false, true);
		this.window.setText(texts.get("arcade.window.title", texts.get("arcade.name")));
		UITableLayout layout = new UITableLayout();
		this.window.setLayout(layout);
		this.canvas = new ArcadeCanvas(factory, this.window, source, ui, System::nanoTime, "Sans");
		layout.set(this.canvas.getControl(), 1, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_FILL, true, true, 1, 1, 960f, 540f, null);

		UITableLayout buttonsLayout = new UITableLayout(0f);
		UIPanel buttons = factory.createPanel(this.window, false);
		buttons.setLayout(buttonsLayout);
		layout.set(buttons, 2, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);
		this.buttonsPanel = buttons;
		this.state = factory.createLabel(buttons);
		buttonsLayout.set(this.state, 1, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, true, false);
		UIButton restart = factory.createButton(buttons);
		restart.setText(texts.get("arcade.button.restart"));
		buttonsLayout.set(restart, 1, 2, UITableLayout.ALIGN_RIGHT, UITableLayout.ALIGN_CENTER, false, false, 1, 1, 110f, 28f, null);
		this.pause = factory.createButton(buttons);
		this.pause.setText(texts.get("arcade.button.pause"));
		buttonsLayout.set(this.pause, 1, 3, UITableLayout.ALIGN_RIGHT, UITableLayout.ALIGN_CENTER, false, false, 1, 1, 90f, 28f, null);
		UIButton stop = factory.createButton(buttons);
		stop.setText(texts.get("arcade.button.stop"));
		buttonsLayout.set(stop, 1, 4, UITableLayout.ALIGN_RIGHT, UITableLayout.ALIGN_CENTER, false, false, 1, 1, 90f, 28f, null);
		this.pause.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				if (ArcadeWindow.this.paused) {
					ArcadeWindow.this.actions.resume();
				} else {
					ArcadeWindow.this.actions.pause();
				}
			}
		});
		restart.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				ArcadeWindow.this.actions.restart();
			}
		});
		stop.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				ArcadeWindow.this.actions.stop();
			}
		});
		// the size of the window changes when it goes full screen and when the system gives it back: the rows are laid out again every time, so that the picture
		// is never taller or wider than the room it has (owner, 2026-10-13: the texts of the top were cut)
		this.window.addResizeListener(event -> {
			if (!this.window.isDisposed()) {
				this.window.layout();
			}
		});
		this.window.addDisposeListener(new UIDisposeListener() {
			public void onDispose(UIDisposeEvent event) {
				ArcadeWindow.this.canvas.dispose();
				if (!ArcadeWindow.this.closing) {
					// the learner closed the window: that is stopping the run
					ArcadeWindow.this.actions.stop();
				}
			}
		});
	}

	/** Shows the window and starts asking for frames. */
	public void open() {
		this.window.pack();
		this.window.open();
		// the game fills the screen (owner, 2026-10-11): maximized, not full screen: on macOS the full screen of the toolkit left the window at its first size while
		// the rows were laid out for the whole screen, so the picture was taller than the room it had and its top, with the texts, was out of sight (2026-10-13)
		this.window.maximize();
		// the size of the window changes once it is shown: the rows are laid out again on the next turn of the UI loop, so that the picture takes all the room
		// and the buttons are at the very bottom
		this.ui.post(() -> {
			if (!this.window.isDisposed()) {
				this.window.layout();
			}
		});
		this.canvas.start();
	}

	public boolean isOpen() {
		return !this.window.isDisposed();
	}

	/** The bounds of the row of the buttons and of the canvas, for the tests of the layout. */
	UIRectangle buttonsBounds() {
		return this.buttonsPanel.getBounds();
	}

	UIRectangle canvasBounds() {
		return this.canvas.getControl().getBounds();
	}

	public UIWindow getWindow() {
		return this.window;
	}

	/** The run is paused or running again: the button and the line under the picture say so. */
	public void setPaused(boolean paused) {
		if (this.window.isDisposed()) {
			return;
		}
		this.paused = paused;
		this.pause.setText(this.texts.get(paused ? "arcade.button.resume" : "arcade.button.pause"));
		this.state.setText(paused ? this.texts.get("arcade.state.paused") : "");
	}

	/** Gives the canvas and the window back; the run is not asked to stop (it is over or being stopped by the caller). */
	public void close() {
		this.closing = true;
		if (!this.window.isDisposed()) {
			this.canvas.stop();
			this.window.dispose();
		}
	}

	/** The cost of the quality controller is read from the source of the canvas by the caller; this is the controller a source is built with. */
	public static QualityController newQuality() {
		return new QualityController(ArcadeCanvas.FRAME_NANOS);
	}
}
