package app.tuxguitar.app.tools.custom.practice.ui;

import java.util.function.Consumer;

import app.tuxguitar.app.tools.custom.practice.session.FollowStarter;
import app.tuxguitar.app.tools.custom.practice.session.PracticeCommand;
import app.tuxguitar.app.tools.custom.practice.session.RemovedSoundsPlayer;
import app.tuxguitar.audio.engine.AudioEngine;
import app.tuxguitar.song.models.TGSong;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.resource.UIImage;
import app.tuxguitar.ui.widget.UIWindow;

/**
 * Everything the practice window takes from the application, in one place (spec 011, owner's decision of 2026-10-15; contract {@code contracts/practice-host.md}). The
 * default implementation, {@link AppPracticeHost}, does exactly what the window did itself before; a test gives another one so that the real window can be built without the
 * whole application. Twelve methods in three groups: the application's, time, and the plugin's. It does not grow without being declared.
 */
interface PracticeHost {

	/** What a run needs and what the tempo-following panel needs, from the same adapter. */
	interface RunEnvironment extends FollowStarter.Environment, FollowPanel.Backend {

		/** Where the notes of the sounds a level of Arcade removed are sent (spec 014), or null when this environment cannot play them. */
		default RemovedSoundsPlayer.Output removedSoundsOutput() {
			return null;
		}
	}

	// ---- the application's
	UIFactory factory();

	UIWindow mainWindow();

	UIImage appIcon();

	TGSong song();

	CaretView caret();

	void openDialog(UIWindow window, int style);

	AudioEngine engine();

	RunEnvironment environment(AudioEngine engine);

	// ---- time
	/** Runs {@code task} on the user-interface thread through the synchronizer after {@code millis}, from a daemon thread called {@code name}. */
	void later(String name, long millis, Runnable task);

	long nanoTime();

	// ---- the plugin's
	void windowStateChanged();

	void commandSink(Consumer<PracticeCommand> sink);
}
