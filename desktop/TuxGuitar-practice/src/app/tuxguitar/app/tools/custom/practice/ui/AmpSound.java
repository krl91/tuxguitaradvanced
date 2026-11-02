package app.tuxguitar.app.tools.custom.practice.ui;

import java.util.List;
import java.util.function.Supplier;

import app.tuxguitar.app.tools.custom.practice.session.AmpSoundSettings;
import app.tuxguitar.app.tools.custom.practice.session.AmpSoundStore;
import app.tuxguitar.audio.engine.input.AudioInputDescriptor;
import app.tuxguitar.audio.engine.monitor.ChainSettings;
import app.tuxguitar.audio.engine.monitor.MonitorEngine;
import app.tuxguitar.audio.engine.monitor.MonitorNotice;
import app.tuxguitar.audio.engine.monitor.MonitorState;
import app.tuxguitar.audio.engine.monitor.NativeMonitorDevice;
import app.tuxguitar.audio.engine.monitor.OutputDevices;
import app.tuxguitar.player.base.MidiPlayer;
import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.TGSynchronizer;

/**
 * The amp sound of one practice window (spec 022, FR-001): one {@link MonitorEngine} over the native device. It starts when *Amp sound* is on and the practice
 * window opens, stops when the practice window closes, and follows the switch, the output, the volume and the chain of the amp window. It opens the practice
 * input (the device of the current input selection, by name, and its channel) on its own: the capture of the recognition is not touched.
 */
final class AmpSound implements AmpWindow.Sound {

	/** What the amp window shows of the engine (UI thread). */
	interface View {
		void stateChanged(MonitorState state, String reason);

		void notice(MonitorNotice notice);

		/** The sound would start on its own with the built-in microphone: the window warns first (FR-012). */
		void warnBeforeStart();
	}

	private final TGContext context;
	private final AmpSoundStore store;
	private final Supplier<AudioInputDescriptor> input;
	private final AmpWindow.Texts texts;
	private final MonitorEngine engine;
	private View view;

	AmpSound(TGContext context, AmpSoundStore store, Supplier<AudioInputDescriptor> input, AmpWindow.Texts texts) {
		this.context = context;
		this.store = store;
		this.input = input;
		this.texts = texts;
		this.engine = MonitorEngine.create(context, new NativeMonitorDevice(), runnable -> TGSynchronizer.getInstance(context).executeLater(runnable));
		this.engine.setChain(store.currentChain());
		this.engine.setGuitarVolume(store.settings().guitarVolume());
		this.engine.addListener(new MonitorEngine.Listener() {
			public void stateChanged(MonitorState state, String reason) {
				if (AmpSound.this.view != null) {
					AmpSound.this.view.stateChanged(state, reason);
				}
			}

			public void notice(MonitorNotice notice) {
				if (AmpSound.this.view != null) {
					AmpSound.this.view.notice(notice);
				}
			}
		});
	}

	void setView(View value) {
		this.view = value;
	}

	/** The practice window opened: the sound starts when it is on. */
	void windowOpened() {
		if (this.store.settings().enabled()) {
			startOrWarn();
		}
	}

	/** The practice input changed: a playing sound opens the new one. */
	void inputChanged() {
		MonitorState state = this.engine.state();
		if (state == MonitorState.ON || state == MonitorState.STARTING) {
			this.engine.stop();
			startOrWarn();
		}
	}

	/** Starts the sound, unless the input is the built-in microphone: then the window warns, and Turn on anyway starts it. */
	private void startOrWarn() {
		if (builtInMicrophone() && this.view != null) {
			this.view.warnBeforeStart();
		} else {
			start();
		}
	}

	/** The practice window closes. */
	void close() {
		this.engine.stop();
	}

	private void start() {
		AudioInputDescriptor descriptor = this.input.get();
		String inputName = descriptor == null || descriptor.isSystemDefault() ? "" : descriptor.getDeviceName();
		int channel = descriptor == null ? -1 : descriptor.getChannelIndex().orElse(-1);
		AmpSoundSettings settings = this.store.settings();
		this.engine.setChain(this.store.currentChain());
		this.engine.setGuitarVolume(settings.guitarVolume());
		this.engine.start(inputName, channel, settings.output());
	}

	public List<String> outputs() {
		return OutputDevices.names();
	}

	public double estimatedDelayMillis() {
		return this.engine.state() == MonitorState.ON || this.engine.state() == MonitorState.STARTING ? this.engine.stats().estimatedDelayMillis() : Double.NaN;
	}

	public void setEnabled(boolean enabled) {
		if (enabled) {
			start();
		} else {
			this.engine.stop();
		}
	}

	public void setOutput(String output) {
		MonitorState state = this.engine.state();
		if (state == MonitorState.ON || state == MonitorState.STARTING) {
			this.engine.stop();
			start();
		}
	}

	public void setGuitarVolume(double volume) {
		this.engine.setGuitarVolume(volume);
	}

	public void setChain(ChainSettings chain) {
		this.engine.setChain(chain);
	}

	public int scoreVolume() {
		return MidiPlayer.getInstance(this.context).getVolume();
	}

	public void setScoreVolume(int volume) {
		MidiPlayer.getInstance(this.context).setVolume(volume);
	}

	public boolean builtInMicrophone() {
		AudioInputDescriptor descriptor = this.input.get();
		return descriptor != null && descriptor.getKind() == AudioInputDescriptor.Kind.BUILT_IN_MICROPHONE;
	}
}
