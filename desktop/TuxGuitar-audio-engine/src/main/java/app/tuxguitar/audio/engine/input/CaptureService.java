package app.tuxguitar.audio.engine.input;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;

import app.tuxguitar.thread.TGThreadManager;
import app.tuxguitar.thread.TGThreadPriority;
import app.tuxguitar.util.TGContext;

/** Shared Java Sound capture loop. Hardware is opened only on its worker thread. */
public final class CaptureService {

	public enum EventType {
		STARTED,
		STOPPED,
		CAPTURE_ERROR,
		DEVICE_LOST
	}

	public static final class Event {
		private final EventType type;
		private final AudioFormat format;
		private final Exception cause;

		private Event(EventType type, AudioFormat format, Exception cause) {
			this.type = type;
			this.format = format;
			this.cause = cause;
		}

		public EventType getType() {
			return this.type;
		}

		public AudioFormat getFormat() {
			return this.format;
		}

		public Exception getCause() {
			return this.cause;
		}
	}

	public interface Listener {
		void captureEvent(Event event);
	}

	private static final int FRAMES_PER_READ = 1024;

	private final TGContext context;
	private final InputDeviceCatalog catalog;
	private final CurrentInputSelection selection;
	private final SampleRingBuffer ringBuffer;
	private final Set<String> consumers;
	private final List<Listener> listeners;
	private long generation;
	private long observedInputRevision;
	private volatile TargetDataLine activeLine;

	public CaptureService(TGContext context, InputDeviceCatalog catalog, CurrentInputSelection selection, SampleRingBuffer ringBuffer) {
		this.context = context;
		this.catalog = catalog;
		this.selection = selection;
		this.ringBuffer = ringBuffer;
		this.consumers = new HashSet<String>();
		this.listeners = new CopyOnWriteArrayList<Listener>();
		this.observedInputRevision = selection.snapshot().getInputRevision();
		this.selection.addListener(this::restartForSelectionChange);
	}

	public synchronized void acquire(String consumerId) {
		validateConsumerId(consumerId);
		if (this.consumers.add(consumerId) && this.consumers.size() == 1) {
			startCaptureLocked();
		}
	}

	public synchronized void release(String consumerId) {
		validateConsumerId(consumerId);
		if (this.consumers.remove(consumerId) && this.consumers.isEmpty()) {
			stopCaptureLocked();
		}
	}

	public synchronized int getConsumerCount() {
		return this.consumers.size();
	}

	public void addListener(Listener listener) {
		this.listeners.add(listener);
	}

	public void removeListener(Listener listener) {
		this.listeners.remove(listener);
	}

	private synchronized void restartForSelectionChange(CurrentInputSelection.Snapshot snapshot) {
		if (snapshot.getInputRevision() != this.observedInputRevision) {
			this.observedInputRevision = snapshot.getInputRevision();
		} else {
			return;
		}
		if (!this.consumers.isEmpty()) {
			stopCaptureLocked();
			startCaptureLocked();
		}
	}

	private void startCaptureLocked() {
		long workerGeneration = ++this.generation;
		CurrentInputSelection.Snapshot snapshot = this.selection.snapshot();
		TGThreadManager.getInstance(this.context).start(TGThreadPriority.HIGH,
				() -> capture(workerGeneration, snapshot));
	}

	private void stopCaptureLocked() {
		this.generation++;
		TargetDataLine line = this.activeLine;
		if (line != null) {
			line.stop();
			line.close();
		}
	}

	private void capture(long workerGeneration, CurrentInputSelection.Snapshot snapshot) {
		TargetDataLine line = null;
		try {
			if (snapshot.getInput() == null) {
				throw new IllegalArgumentException("no audio input selected");
			}
			Mixer mixer = this.catalog.resolveMixer(snapshot.getInput());
			AudioFormat format = chooseFormat(mixer, snapshot.getInput(), this.catalog.advertisedFormats(snapshot.getInput()));
			DataLine.Info lineInfo = new DataLine.Info(TargetDataLine.class, format);
			line = (TargetDataLine) mixer.getLine(lineInfo);
			line.open(format, format.getFrameSize() * FRAMES_PER_READ * 4);
			synchronized (this) {
				if (workerGeneration != this.generation || this.consumers.isEmpty()) {
					line.close();
					return;
				}
				this.activeLine = line;
			}
			line.start();
			fire(new Event(EventType.STARTED, format, null));
			captureSamples(line, format, snapshot.getInput(), workerGeneration);
		} catch (Exception exception) {
			if (isCurrent(workerGeneration)) {
				fire(new Event(EventType.CAPTURE_ERROR, null, exception));
			}
		} finally {
			if (line != null) {
				try {
					line.stop();
				} catch (RuntimeException exception) {
					// A disappearing device may already have invalidated the line.
				} finally {
					line.close();
				}
			}
			synchronized (this) {
				if (this.activeLine == line) {
					this.activeLine = null;
				}
			}
			if (isCurrent(workerGeneration)) {
				fire(new Event(EventType.STOPPED, null, null));
			}
		}
	}

	private void captureSamples(TargetDataLine line, AudioFormat format, AudioInputDescriptor input, long workerGeneration) {
		int frameSize = format.getFrameSize();
		byte[] bytes = new byte[frameSize * FRAMES_PER_READ];
		float[] interleaved = new float[FRAMES_PER_READ * format.getChannels()];
		float[] selected = new float[FRAMES_PER_READ];
		int selectedChannel = input.getChannelIndex().orElse(0);
		while (isCurrent(workerGeneration) && line.isOpen()) {
			int byteCount;
			try {
				byteCount = line.read(bytes, 0, bytes.length);
			} catch (RuntimeException exception) {
				if (isCurrent(workerGeneration)) {
					fire(new Event(EventType.DEVICE_LOST, format, exception));
				}
				return;
			}
			if (byteCount <= 0) {
				if (isCurrent(workerGeneration)) {
					fire(new Event(EventType.DEVICE_LOST, format,
							new IllegalStateException("audio input stopped delivering samples")));
				}
				return;
			}
			int sampleCount = PcmSampleConverter.convert(bytes, byteCount, format, interleaved);
			int frameCount = sampleCount / format.getChannels();
			ChannelDeinterleaver.deinterleave(interleaved, frameCount, format.getChannels(), selectedChannel, selected);
			this.ringBuffer.write(selected, 0, frameCount);
		}
	}

	private AudioFormat chooseFormat(Mixer mixer, AudioInputDescriptor input, List<AudioFormat> advertised) {
		AudioFormat preferred = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 48000f, 16,
				input.getChannelCount(), input.getChannelCount() * 2, 48000f, false);
		if (mixer.isLineSupported(new DataLine.Info(TargetDataLine.class, preferred))) {
			return preferred;
		}
		for (int bits : new int[] { 16, 24 }) {
			for (AudioFormat candidate : advertised) {
				if (candidate.getSampleSizeInBits() == bits) {
					float rate = candidate.getSampleRate() > 0 ? candidate.getSampleRate() : 48000f;
					int frameSize = input.getChannelCount() * (bits / 8);
					AudioFormat usable = new AudioFormat(candidate.getEncoding(), rate, bits,
							input.getChannelCount(), frameSize, rate, candidate.isBigEndian());
					if (mixer.isLineSupported(new DataLine.Info(TargetDataLine.class, usable))) {
						return usable;
					}
				}
			}
		}
		throw new IllegalArgumentException("no supported 16/24-bit PCM format for " + input.getDeviceName());
	}

	private synchronized boolean isCurrent(long workerGeneration) {
		return workerGeneration == this.generation && !this.consumers.isEmpty();
	}

	private void fire(Event event) {
		TGThreadManager.getInstance(this.context).start(() -> {
			for (Listener listener : this.listeners) {
				try {
					listener.captureEvent(event);
				} catch (RuntimeException exception) {
					// One consumer must not prevent delivery to the others.
				}
			}
		});
	}

	private static void validateConsumerId(String consumerId) {
		if (consumerId == null || consumerId.isEmpty()) {
			throw new IllegalArgumentException("consumerId is required");
		}
	}
}
