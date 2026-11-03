package app.tuxguitar.audio.engine.backend;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import javax.sound.sampled.AudioFormat;

import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.analysis.AnalysisService;
import app.tuxguitar.audio.engine.analysis.HopListener;
import app.tuxguitar.audio.engine.analysis.StrumClipMeter;
import app.tuxguitar.audio.engine.analysis.StrumVerdict;
import app.tuxguitar.audio.engine.input.AudioInputDescriptor;
import app.tuxguitar.audio.engine.input.CaptureClock;
import app.tuxguitar.audio.engine.input.CaptureService;
import app.tuxguitar.audio.engine.input.CurrentInputSelection;
import app.tuxguitar.audio.engine.nativeengine.FrameRecords;
import app.tuxguitar.audio.engine.nativeengine.NativeRecognitionException;
import app.tuxguitar.audio.engine.nativeengine.NativeRecognitionLibrary;
import app.tuxguitar.audio.engine.settings.PitchEngineCatalog;
import app.tuxguitar.thread.TGThreadManager;
import app.tuxguitar.util.TGContext;

/**
 * The native engine (spec 023, research R4): the crate captures the practice input and analyses it on its own thread; a Java frame thread turns its
 * records into {@link AnalysisFrame}s for the subscribers, and a Java hop thread hands its hop blocks to the hop listeners while there are any. The
 * capture clock is fed from the records' sample end and arrival, so the timing code of the practice is unchanged.
 */
public final class NativeBackend implements RecognitionBackend {

	/** The frames of one native capture block. */
	public static final int BLOCK_FRAMES = 128;
	public static final int HOP_SIZE = 256;
	private static final int HOP_BLOCK = 1 + HOP_SIZE;
	private static final int WAIT_MILLIS = 50;
	private static final long JOIN_MILLIS = 500L;

	/** A hop listener and the flag that tells the hop thread to call its configure before its next hop (as {@code AnalysisService}). */
	private static final class HopEntry {
		final HopListener listener;
		volatile boolean needsConfigure = true;

		HopEntry(HopListener listener) {
			this.listener = listener;
		}
	}

	private final TGContext context;
	private final CurrentInputSelection selection;
	private final NativeRecognitionLibrary.Api api;
	private final Supplier<float[]> parameters;
	private final Set<String> consumers = new HashSet<String>();
	private final List<AnalysisService.Listener> listeners = new CopyOnWriteArrayList<AnalysisService.Listener>();
	private final List<CaptureService.Listener> captureListeners = new CopyOnWriteArrayList<CaptureService.Listener>();
	private volatile HopEntry[] hopListeners = new HopEntry[0];
	private final Object hopListenersLock = new Object();
	private final CaptureClock clock = new CaptureClock(0);
	private final AtomicLong droppedSamples = new AtomicLong();
	private volatile CaptureService.FormatListener formatListener;

	private volatile boolean spectrum;
	private volatile int[] expectedPitches;
	private volatile int[] chordRequired;
	private volatile int[] chordTolerated;
	private volatile int[] attackCandidates;

	private volatile long handle;
	private volatile AudioFormat format;
	private long observedInputRevision;
	private Thread frameThread;
	private Thread hopThread;

	public NativeBackend(TGContext context, CurrentInputSelection selection, NativeRecognitionLibrary.Api api, Supplier<float[]> parameters) {
		this.context = context;
		this.selection = selection;
		this.api = api;
		this.parameters = parameters;
		this.observedInputRevision = selection.snapshot().getInputRevision();
		this.selection.addListener(this::selectionChanged);
	}

	/** Told synchronously at each open the format the input was opened with, before the first frame. */
	public void setFormatListener(CaptureService.FormatListener formatListener) {
		this.formatListener = formatListener;
	}

	@Override
	public PitchEngineCatalog.Kind engineKind() {
		return PitchEngineCatalog.Kind.NATIVE;
	}

	/**
	 * Opens the selected input (the system default as {@code ""}, its channel or −1) at the device's current rate, gives it what was asked so far and
	 * starts the frame and hop threads. Nothing happens when it is open.
	 */
	public synchronized void open() throws NativeRecognitionException {
		if( this.handle != 0 ) {
			return;
		}
		CurrentInputSelection.Snapshot snapshot = this.selection.snapshot();
		this.observedInputRevision = snapshot.getInputRevision();
		AudioInputDescriptor input = snapshot.getInput();
		String name = (input == null || input.isSystemDefault() ? "" : input.getDeviceName());
		int channel = (input == null ? -1 : input.getChannelIndex().orElse(-1));
		long opened = this.api.open(name, channel, BLOCK_FRAMES, System.nanoTime(), this.parameters.get());
		float rate = this.api.sampleRate(opened);
		// the route of the practice knows the input by its channels and a 16-bit profile, as the Java capture opens it
		int channels = (input != null ? input.getChannelCount() : 1);
		this.format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, rate, 16, channels, channels * 2, rate, false);
		CaptureService.FormatListener told = this.formatListener;
		if( told != null ) {
			told.formatNegotiated(this.format);
		}
		this.clock.configure(Math.round(rate));
		this.api.setSpectrumEnabled(opened, this.spectrum);
		this.api.setExpectedPitches(opened, this.expectedPitches);
		this.api.setExpectedChord(opened, this.chordRequired, this.chordTolerated);
		this.api.setAttackCandidates(opened, this.attackCandidates);
		this.api.setHopsEnabled(opened, this.hopListeners.length > 0);
		this.handle = opened;
		int hopRate = Math.round(rate);
		this.frameThread = start("tuxguitar-native-frames", () -> frameLoop(opened));
		this.hopThread = start("tuxguitar-native-hops", () -> hopLoop(opened, hopRate));
		fire(CaptureService.EventType.STARTED, null);
	}

	/** Stops the stream and the threads (within {@value #JOIN_MILLIS} ms). Idempotent. */
	public synchronized void close() {
		long opened = this.handle;
		if( opened == 0 ) {
			return;
		}
		this.handle = 0;
		this.api.close(opened);
		join(this.frameThread);
		join(this.hopThread);
		this.frameThread = null;
		this.hopThread = null;
		fire(CaptureService.EventType.STOPPED, null);
	}

	/** Whether the frame or the hop thread still runs. */
	public synchronized boolean isRunning() {
		return (this.frameThread != null && this.frameThread.isAlive()) || (this.hopThread != null && this.hopThread.isAlive());
	}

	/** The format of the open input, or null. */
	public AudioFormat getFormat() {
		return this.format;
	}

	@Override
	public synchronized void acquire(String consumerId) {
		if( this.consumers.add(consumerId) && this.consumers.size() == 1 ) {
			openOrReport();
		}
	}

	@Override
	public synchronized void release(String consumerId) {
		if( this.consumers.remove(consumerId) && this.consumers.isEmpty() ) {
			close();
		}
	}

	@Override
	public synchronized int getConsumerCount() {
		return this.consumers.size();
	}

	private void openOrReport() {
		try {
			open();
		} catch (NativeRecognitionException exception) {
			fire(CaptureService.EventType.CAPTURE_ERROR, exception);
		}
	}

	/** A new input while the capture is held: the stream starts again on it (as {@code CaptureService}). */
	private synchronized void selectionChanged(CurrentInputSelection.Snapshot snapshot) {
		if( snapshot.getInputRevision() == this.observedInputRevision ) {
			return;
		}
		this.observedInputRevision = snapshot.getInputRevision();
		if( !this.consumers.isEmpty() ) {
			close();
			openOrReport();
		}
	}

	@Override
	public void subscribe(AnalysisService.Listener listener) {
		this.listeners.add(listener);
	}

	@Override
	public void unsubscribe(AnalysisService.Listener listener) {
		this.listeners.remove(listener);
	}

	@Override
	public void addHopListener(HopListener listener) {
		synchronized (this.hopListenersLock) {
			HopEntry[] current = this.hopListeners;
			HopEntry[] next = java.util.Arrays.copyOf(current, current.length + 1);
			next[current.length] = new HopEntry(listener);
			this.hopListeners = next;
			hopsChanged();
		}
	}

	@Override
	public void removeHopListener(HopListener listener) {
		synchronized (this.hopListenersLock) {
			List<HopEntry> kept = new ArrayList<HopEntry>();
			for( HopEntry entry : this.hopListeners ) {
				if( entry.listener != listener ) {
					kept.add(entry);
				}
			}
			this.hopListeners = kept.toArray(new HopEntry[0]);
			hopsChanged();
		}
	}

	private void hopsChanged() {
		long opened = this.handle;
		if( opened != 0 ) {
			this.api.setHopsEnabled(opened, this.hopListeners.length > 0);
		}
	}

	@Override
	public void addCaptureListener(CaptureService.Listener listener) {
		this.captureListeners.add(listener);
	}

	@Override
	public void removeCaptureListener(CaptureService.Listener listener) {
		this.captureListeners.remove(listener);
	}

	@Override
	public void setSpectrumEnabled(boolean enabled) {
		this.spectrum = enabled;
		long opened = this.handle;
		if( opened != 0 ) {
			this.api.setSpectrumEnabled(opened, enabled);
		}
	}

	@Override
	public void setExpectedPitches(int[] midi) {
		this.expectedPitches = (midi == null || midi.length == 0 ? null : midi.clone());
		long opened = this.handle;
		if( opened != 0 ) {
			this.api.setExpectedPitches(opened, this.expectedPitches);
		}
	}

	@Override
	public void setExpectedChord(int[] required, int[] tolerated) {
		this.chordRequired = (required == null ? null : required.clone());
		this.chordTolerated = (required == null ? null : (tolerated == null ? new int[0] : tolerated.clone()));
		long opened = this.handle;
		if( opened != 0 ) {
			this.api.setExpectedChord(opened, this.chordRequired, this.chordTolerated);
		}
	}

	@Override
	public void setAttackCandidates(int[] pitches) {
		this.attackCandidates = (pitches == null || pitches.length == 0 ? null : pitches.clone());
		long opened = this.handle;
		if( opened != 0 ) {
			this.api.setAttackCandidates(opened, this.attackCandidates);
		}
	}

	@Override
	public void configure(float[] values) {
		long opened = this.handle;
		if( opened != 0 ) {
			this.api.configure(opened, values);
		}
	}

	@Override
	public CaptureClock clock() {
		return this.clock;
	}

	@Override
	public long getDroppedSampleCount() {
		return this.droppedSamples.get();
	}

	/** The native strum clip measure's reading, for the pre-run check (research R2); no strum while closed. */
	public StrumClipMeter.Reading strumReading() {
		long opened = this.handle;
		return (opened == 0 ? readingOf(null) : readingOf(this.api.strumClip(opened)));
	}

	/** Starts a new strum clip check. */
	public void resetStrumReading() {
		long opened = this.handle;
		if( opened != 0 ) {
			this.api.resetStrumClip(opened);
		}
	}

	/** The dropped blocks and the median and 99th percentile of one hop's analysis cost in nanoseconds; null while closed. */
	public float[] stats() {
		long opened = this.handle;
		return (opened == 0 ? null : this.api.stats(opened));
	}

	/** A reading of the crate (verdict ordinal, clipped share, RMS dBFS) as the Java meter's. */
	public static StrumClipMeter.Reading readingOf(float[] values) {
		if( values == null || values.length < 3 ) {
			return new StrumClipMeter.Reading(StrumVerdict.NO_STRUM, 0.0, Double.NaN);
		}
		StrumVerdict[] verdicts = StrumVerdict.values();
		int ordinal = (int) values[0];
		StrumVerdict verdict = (ordinal >= 0 && ordinal < verdicts.length ? verdicts[ordinal] : StrumVerdict.NO_STRUM);
		return new StrumClipMeter.Reading(verdict, values[1], values[2]);
	}

	private void frameLoop(long opened) {
		float[] records = new float[FrameRecords.CAPACITY * 8];
		long seenDropped = 0;
		while( true ) {
			int count = this.api.nextFrames(opened, records, WAIT_MILLIS);
			if( count == -2 ) {
				return;
			}
			if( count == -1 ) {
				if( this.handle == opened ) {
					fire(CaptureService.EventType.DEVICE_LOST, new IllegalStateException("audio input stopped delivering samples"));
				}
				return;
			}
			if( count == 0 ) {
				continue;
			}
			long dropped = this.api.droppedBlocks(opened);
			if( dropped != seenDropped ) {
				// samples were lost: the index of the stream no longer follows time, the estimate starts again (as the Java capture)
				this.droppedSamples.addAndGet((dropped - seenDropped) * BLOCK_FRAMES);
				seenDropped = dropped;
				this.clock.reset();
			}
			int offset = 0;
			for( int i = 0; i < count; i++ ) {
				AnalysisFrame frame = FrameRecords.read(records, offset);
				this.clock.onSamples(FrameRecords.arrivalNanos(records, offset), frame.getCaptureSampleEnd());
				publish(frame);
				offset += FrameRecords.length(records, offset);
			}
		}
	}

	private void publish(AnalysisFrame frame) {
		for( AnalysisService.Listener listener : this.listeners ) {
			try {
				listener.analysisFrame(frame);
			} catch (RuntimeException exception) {
				// Analysis continues even if one consumer rejects a frame.
			}
		}
	}

	private void hopLoop(long opened, int streamRate) {
		float[] hops = new float[HOP_BLOCK * 16];
		int rate = streamRate;
		while( true ) {
			int count = this.api.nextHops(opened, hops, WAIT_MILLIS);
			if( count < 0 ) {
				return;
			}
			for( int b = 0; b < count; b++ ) {
				int at = b * HOP_BLOCK;
				HopEntry[] entries = this.hopListeners;
				if( hops[at] != 0f ) {
					// a configure marker: the rate of the hops that follow, told to every listener before its next hop
					rate = Math.round(hops[at]);
					for( HopEntry entry : entries ) {
						entry.needsConfigure = true;
					}
					continue;
				}
				for( HopEntry entry : entries ) {
					try {
						if( entry.needsConfigure ) {
							entry.needsConfigure = false;
							entry.listener.configure(rate);
						}
						entry.listener.hop(hops, at + 1, HOP_SIZE);
					} catch (RuntimeException exception) {
						// one listener must not stop the others
					}
				}
			}
		}
	}

	private void fire(CaptureService.EventType type, Exception cause) {
		CaptureService.Event event = CaptureService.Event.of(type, this.format, cause);
		TGThreadManager.getInstance(this.context).start(() -> {
			for( CaptureService.Listener listener : this.captureListeners ) {
				try {
					listener.captureEvent(event);
				} catch (RuntimeException exception) {
					// One consumer must not prevent delivery to the others.
				}
			}
		});
	}

	private static Thread start(String name, Runnable body) {
		Thread thread = new Thread(body, name);
		thread.setDaemon(true);
		thread.setPriority(Thread.MAX_PRIORITY);
		thread.start();
		return thread;
	}

	private static void join(Thread thread) {
		if( thread == null || thread == Thread.currentThread() ) {
			return;
		}
		try {
			thread.join(JOIN_MILLIS);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
		}
	}
}
