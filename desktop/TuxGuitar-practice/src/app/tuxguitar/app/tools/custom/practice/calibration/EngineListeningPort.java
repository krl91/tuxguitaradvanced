package app.tuxguitar.app.tools.custom.practice.calibration;

import java.util.concurrent.Executor;
import java.util.function.IntSupplier;

import app.tuxguitar.audio.engine.AudioEngine;
import app.tuxguitar.audio.engine.analysis.AnalysisService;
import app.tuxguitar.audio.engine.analysis.HopListener;
import app.tuxguitar.audio.engine.calibration.FrontEndState;
import app.tuxguitar.audio.engine.input.CaptureService;

/**
 * The engine side of the listening mode (spec 013): holds the capture for the calibration, keeps the analysis running (it runs while a frame listener is
 * subscribed) and gives the controller's listeners every hop of it. {@link #stop()} may be called from the analysis thread, by the listener that ended the
 * listening: it detaches at once and lets go of the capture on another thread.
 */
public final class EngineListeningPort implements CalibrationController.ListeningPort {

	/** The name under which the calibration holds the capture. */
	static final String CONSUMER = "recognition-calibration";

	/** Holds and lets go of the capture of the selected input; the engine does it in the application, nothing in a test that feeds the engine by hand. */
	public interface CaptureHold {
		void acquire(String consumer);

		void release(String consumer);
	}

	private final AudioEngine engine;
	private final CaptureHold capture;
	private final Executor background;
	private final IntSupplier sampleRate;
	private final AnalysisService.Listener keepsAnalysisRunning = frame -> {
	};
	private final CaptureService.Listener captureListener = event -> deviceEvent(event.getType());
	private HopListener[] attached;
	private volatile Runnable deviceLost;

	/**
	 * @param background where letting go of the capture is done (it can wait for the capture thread)
	 * @param sampleRate the capture rate now, 0 when not known
	 */
	public EngineListeningPort(AudioEngine engine, Executor background, IntSupplier sampleRate) {
		this(engine, new CaptureHold() {
			public void acquire(String consumer) {
				engine.acquire(consumer);
			}

			public void release(String consumer) {
				engine.release(consumer);
			}
		}, background, sampleRate);
	}

	public EngineListeningPort(AudioEngine engine, CaptureHold capture, Executor background, IntSupplier sampleRate) {
		this.engine = engine;
		this.capture = capture;
		this.background = background;
		this.sampleRate = sampleRate;
	}

	/** Told when the input is lost or its capture fails while the port holds it. */
	public void setDeviceLostListener(Runnable listener) {
		this.deviceLost = listener;
	}

	@Override
	public synchronized void start(HopListener... listeners) {
		if (this.attached != null) {
			stop();
		}
		this.attached = listeners;
		this.engine.addCaptureListener(this.captureListener);
		this.capture.acquire(CONSUMER);
		for (HopListener listener : listeners) {
			this.engine.addHopListener(listener);
		}
		this.engine.subscribe(this.keepsAnalysisRunning);
	}

	@Override
	public synchronized void stop() {
		if (this.attached == null) {
			return;
		}
		for (HopListener listener : this.attached) {
			this.engine.removeHopListener(listener);
		}
		this.attached = null;
		this.engine.unsubscribe(this.keepsAnalysisRunning);
		this.engine.removeCaptureListener(this.captureListener);
		this.background.execute(() -> this.capture.release(CONSUMER));
	}

	@Override
	public FrontEndState frontEnd() {
		return new FrontEndState(this.engine.getInputGain(), this.engine.isNoiseReductionEnabled());
	}

	@Override
	public int sampleRate() {
		return this.sampleRate.getAsInt();
	}

	void deviceEvent(CaptureService.EventType type) {
		Runnable told = this.deviceLost;
		if (told != null && (type == CaptureService.EventType.DEVICE_LOST || type == CaptureService.EventType.CAPTURE_ERROR)) {
			told.run();
		}
	}
}
