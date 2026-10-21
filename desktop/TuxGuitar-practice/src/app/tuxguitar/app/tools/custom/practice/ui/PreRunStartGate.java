package app.tuxguitar.app.tools.custom.practice.ui;

import java.util.function.Supplier;

import app.tuxguitar.app.TuxGuitar;
import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeLevel;
import app.tuxguitar.app.tools.custom.practice.session.ArcadeChoices;
import app.tuxguitar.app.tools.custom.practice.session.FollowSettings;
import app.tuxguitar.app.tools.custom.practice.ui.PreRunCheck.State;
import app.tuxguitar.audio.engine.AudioEngine;
import app.tuxguitar.util.TGContext;

/**
 * The real gate (spec 010): opens the small start window with the audio of the engine. The engine, the factory and the parent are read when the start is
 * asked, because the practice window builds them in {@code open()}, after this object exists.
 */
final class PreRunStartGate implements StartGate {

	private static final PreRunWindow.Texts TEXTS = (key, arguments) -> arguments.length == 0 ? TuxGuitar.getProperty(key) : TuxGuitar.getProperty(key, arguments);

	private final TGContext context;
	private final Supplier<AudioEngine> engine;
	private final PracticeHost host;
	private final Runnable onCancelled;
	private PreRunWindow current;

	PreRunStartGate(TGContext context, PracticeHost host, Supplier<AudioEngine> engine, Runnable onCancelled) {
		this.context = context;
		this.engine = engine;
		this.host = host;
		this.onCancelled = onCancelled;
	}

	@Override
	public void request(PreRunCheck check, Runnable onReady) {
		PreRunWindow window = new PreRunWindow(this.host.factory(), this.host.mainWindow(), check, new EnginePreRunSource(this.context, this.engine.get()), TEXTS, this.host::nanoTime,
				new PracticeTunerOpener(this.context), () -> {
					this.current = null;
					if (check.state() == State.READY) {
						onReady.run();
					} else if (check.state() == State.CANCELLED) {
						this.onCancelled.run();
					}
				}, levelChoiceFor(check));
		this.current = window;
		window.refresh();
		window.open();
	}

	/** An Arcade start asks the level of the run (spec 014, FR-006): the last one chosen is proposed, level 1 the first time, and a change is kept at once. */
	private PreRunWindow.LevelChoice levelChoiceFor(PreRunCheck check) {
		if (check.mode() != FollowSettings.Mode.ARCADE) {
			return null;
		}
		final ArcadeChoices choices = ArcadeChoices.of(this.context);
		return new PreRunWindow.LevelChoice() {
			public ArcadeLevel level() {
				return choices.level();
			}

			public void choose(ArcadeLevel level) {
				choices.setLevel(level);
			}
		};
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
