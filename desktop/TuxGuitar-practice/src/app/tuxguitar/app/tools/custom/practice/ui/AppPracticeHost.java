package app.tuxguitar.app.tools.custom.practice.ui;

import java.util.function.Consumer;

import app.tuxguitar.app.TuxGuitar;
import app.tuxguitar.app.tools.custom.practice.session.PracticeCommand;
import app.tuxguitar.app.ui.TGApplication;
import app.tuxguitar.app.view.component.tab.Caret;
import app.tuxguitar.app.view.main.TGWindow;
import app.tuxguitar.app.view.util.TGDialogUtil;
import app.tuxguitar.audio.engine.AudioEngine;
import app.tuxguitar.song.models.TGSong;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.resource.UIImage;
import app.tuxguitar.ui.widget.UIWindow;
import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.TGSynchronizer;

/** The practice window's access to the application, exactly as it was written inside the window before the seam (spec 011); see {@link PracticeHost}. */
final class AppPracticeHost implements PracticeHost {

	private final TGContext context;
	private final PracticePlugin plugin;

	AppPracticeHost(TGContext context, PracticePlugin plugin) {
		this.context = context;
		this.plugin = plugin;
	}

	@Override
	public UIFactory factory() {
		return TGApplication.getInstance(this.context).getFactory();
	}

	@Override
	public UIWindow mainWindow() {
		return TGWindow.getInstance(this.context).getWindow();
	}

	@Override
	public UIImage appIcon() {
		return TuxGuitar.getInstance().getIconManager().getAppIcon();
	}

	@Override
	public TGSong song() {
		return TuxGuitar.getInstance().getDocumentManager().getSong();
	}

	@Override
	public CaretView caret() {
		Caret caret = TuxGuitar.getInstance().getTablatureEditor().getTablature().getCaret();
		return new CaretView(caret.getTrack(), caret.getMeasure(), caret.getPosition());
	}

	@Override
	public void openDialog(UIWindow window, int style) {
		TGDialogUtil.openDialog(window, style);
	}

	@Override
	public AudioEngine engine() {
		return AudioEngine.getInstance(this.context);
	}

	@Override
	public RunEnvironment environment(AudioEngine engine) {
		return new EngineFollowAdapter(this.context, engine);
	}

	@Override
	public void later(String name, long millis, Runnable task) {
		Thread thread = new Thread(() -> {
			try {
				Thread.sleep(millis);
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				return;
			}
			TGSynchronizer.getInstance(this.context).executeLater(task);
		}, name);
		thread.setDaemon(true);
		thread.start();
	}

	@Override
	public long nanoTime() {
		return System.nanoTime();
	}

	@Override
	public void windowStateChanged() {
		this.plugin.windowStateChanged(this.context);
	}

	@Override
	public void commandSink(Consumer<PracticeCommand> sink) {
		this.plugin.setCommandSink(sink);
	}
}
