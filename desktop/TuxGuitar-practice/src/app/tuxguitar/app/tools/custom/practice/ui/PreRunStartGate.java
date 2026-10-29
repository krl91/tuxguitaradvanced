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
	/** The route, its calibrations and the calibration of the Mode tab, for the offer of the rhythm calibration (spec 016); read when the start is asked. */
	private final Supplier<FollowPanel.Backend> backend;
	private PreRunWindow current;

	PreRunStartGate(TGContext context, PracticeHost host, Supplier<AudioEngine> engine, Runnable onCancelled) {
		this(context, host, engine, onCancelled, () -> null);
	}

	PreRunStartGate(TGContext context, PracticeHost host, Supplier<AudioEngine> engine, Runnable onCancelled, Supplier<FollowPanel.Backend> backend) {
		this.context = context;
		this.engine = engine;
		this.host = host;
		this.onCancelled = onCancelled;
		this.backend = backend;
	}

	@Override
	public void request(PreRunCheck check, Runnable onReady) {
		final FollowPanel.Backend rhythm = this.backend.get();
		RhythmOffer offer = (rhythm != null ? new RhythmOffer(check.mode(), rhythm::route, rhythm.calibrations(), DeclinedRoutes.of(this.context)) : null);
		final PreRunWindow[] made = new PreRunWindow[1];
		PreRunWindow window = new PreRunWindow(this.host.factory(), this.host.mainWindow(), check, new EnginePreRunSource(this.context, this.engine.get()), TEXTS, this.host::nanoTime,
				new PracticeTunerOpener(this.context), () -> {
					this.current = null;
					if (made[0].mayBegin()) {
						onReady.run();
					} else if (check.state() == State.CANCELLED || check.state() == State.READY) {
						// a start cancelled during the offer of the rhythm calibration: the check was ready, the offer was not
						this.onCancelled.run();
					}
				}, levelChoiceFor(check), offer, rhythm);
		made[0] = window;
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
