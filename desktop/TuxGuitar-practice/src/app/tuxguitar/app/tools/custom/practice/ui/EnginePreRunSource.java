package app.tuxguitar.app.tools.custom.practice.ui;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import app.tuxguitar.audio.engine.AudioEngine;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.analysis.AnalysisService;
import app.tuxguitar.audio.engine.analysis.StrumClipMeter;
import app.tuxguitar.audio.engine.settings.RecognitionSettingsStore;
import app.tuxguitar.util.TGSynchronizer;
import app.tuxguitar.util.TGContext;

/**
 * The real source of the start window (spec 010 R6): a strum meter fed by the hops of the engine, and the live level from its frames, as the level panel does.
 * The capture is the one the preparation window holds (it is open, hidden or not, at every start): this only adds and removes its own listeners.
 */
final class EnginePreRunSource implements PreRunSource {

	private static final long UI_INTERVAL_NANOS = 33_000_000L;

	private final TGContext context;
	private final AudioEngine engine;
	private final StrumClipMeter meter;
	private final AnalysisService.Listener frameListener;
	private final AtomicReference<AnalysisFrame> latest = new AtomicReference<AnalysisFrame>();
	private final AtomicBoolean uiPending = new AtomicBoolean();
	private volatile long lastUiNanos;
	private volatile Runnable changeListener;
	private boolean closed;

	EnginePreRunSource(TGContext context, AudioEngine engine) {
		this.context = context;
		this.engine = engine;
		this.meter = new StrumClipMeter(StrumClipMeter.Parameters.of(RecognitionSettingsStore.getInstance(context).get()));
		this.frameListener = this::frameReceived;
		engine.addHopListener(this.meter);
		engine.subscribe(this.frameListener); // the analysis runs while a frame listener is subscribed
	}

	@Override
	public StrumClipMeter.Reading reading() {
		return this.meter.snapshot();
	}

	@Override
	public double levelDbfs() {
		AnalysisFrame frame = this.latest.get();
		return frame == null ? Double.NaN : frame.getRmsDbfs();
	}

	@Override
	public boolean boostActive() {
		return this.engine.getInputGain() > 1.0;
	}

	@Override
	public void reset() {
		this.meter.reset();
	}

	@Override
	public void setChangeListener(Runnable listener) {
		this.changeListener = listener;
	}

	@Override
	public synchronized void close() {
		if (!this.closed) {
			this.closed = true;
			this.changeListener = null;
			this.engine.removeHopListener(this.meter);
			this.engine.unsubscribe(this.frameListener);
		}
	}

	/** Analysis thread: keeps the latest frame and schedules at most about thirty refreshes a second on the user-interface thread. */
	private void frameReceived(AnalysisFrame frame) {
		this.latest.set(frame);
		long now = System.nanoTime();
		if (now - this.lastUiNanos >= UI_INTERVAL_NANOS && this.uiPending.compareAndSet(false, true)) {
			this.lastUiNanos = now;
			TGSynchronizer.getInstance(this.context).executeLater(() -> {
				this.uiPending.set(false);
				Runnable listener = this.changeListener;
				if (listener != null) {
					listener.run();
				}
			});
		}
	}
}
