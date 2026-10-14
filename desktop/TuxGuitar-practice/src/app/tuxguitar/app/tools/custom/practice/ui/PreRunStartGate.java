package app.tuxguitar.app.tools.custom.practice.ui;

import java.util.function.Supplier;

import app.tuxguitar.app.TuxGuitar;
import app.tuxguitar.app.tools.custom.practice.ui.PreRunCheck.State;
import app.tuxguitar.audio.engine.AudioEngine;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.widget.UIWindow;
import app.tuxguitar.util.TGContext;

/**
 * The real gate (spec 010): opens the small start window with the audio of the engine. The engine, the factory and the parent are read when the start is
 * asked, because the practice window builds them in {@code open()}, after this object exists.
 */
final class PreRunStartGate implements StartGate {

	private static final PreRunWindow.Texts TEXTS = (key, arguments) -> arguments.length == 0 ? TuxGuitar.getProperty(key) : TuxGuitar.getProperty(key, arguments);

	private final TGContext context;
	private final Supplier<AudioEngine> engine;
	private final Supplier<UIFactory> factory;
	private final Supplier<UIWindow> parent;
	private final Runnable onCancelled;
	private PreRunWindow current;

	PreRunStartGate(TGContext context, Supplier<AudioEngine> engine, Supplier<UIFactory> factory, Supplier<UIWindow> parent, Runnable onCancelled) {
		this.context = context;
		this.engine = engine;
		this.factory = factory;
		this.parent = parent;
		this.onCancelled = onCancelled;
	}

	@Override
	public void request(PreRunCheck check, Runnable onReady) {
		PreRunWindow window = new PreRunWindow(this.factory.get(), this.parent.get(), check, new EnginePreRunSource(this.context, this.engine.get()), TEXTS, System::nanoTime,
				new PracticeTunerOpener(this.context), () -> {
					this.current = null;
					if (check.state() == State.READY) {
						onReady.run();
					} else if (check.state() == State.CANCELLED) {
						this.onCancelled.run();
					}
				});
		this.current = window;
		window.refresh();
		window.open();
	}

	@Override
	public void toFront() {
		if (this.current != null) {
			this.current.toFront();
		}
	}

	@Override
	public void closePending() {
		if (this.current != null) {
			this.current.refresh(); // the check was cancelled: the refresh sees it and gives the hand back
		}
	}
}
