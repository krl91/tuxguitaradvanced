package app.tuxguitar.audio.engine.analysis;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import app.tuxguitar.audio.engine.frontend.FrontEnd;
import app.tuxguitar.audio.engine.input.SampleRingBuffer;
import app.tuxguitar.audio.engine.recognition.ExpectedPitchVerifier;
import app.tuxguitar.audio.engine.recognition.TargetEvidence;
import app.tuxguitar.thread.TGThreadManager;
import app.tuxguitar.thread.TGThreadPriority;
import app.tuxguitar.util.TGContext;

/**
 * Dedicated sliding-window analysis worker.
 *
 * <p>At most one worker thread exists at a time, so the ring buffer always has a single
 * consumer: when the last subscriber leaves, the worker exits at its next loop check; if a
 * subscriber arrives before that, the same worker simply continues. A new sample rate and
 * estimator ({@link #reconfigure}) are applied by the worker itself at the top of its loop.</p>
 */
public final class AnalysisService {

	public interface Listener {
		void analysisFrame(AnalysisFrame frame);
	}

	/** Everything the worker switches at once, at the top of its loop (never a partial state). */
	private static final class Configuration {
		private final int sampleRate;
		private final PitchEstimator pitchEstimator;
		private final long discardUntil;
		private final LevelAnalyzer levelAnalyzer;
		private final ArticulationDetector articulationDetector;

		private Configuration(int sampleRate, PitchEstimator pitchEstimator, long discardUntil,
				LevelAnalyzer levelAnalyzer, ArticulationDetector articulationDetector) {
			this.sampleRate = sampleRate;
			this.pitchEstimator = pitchEstimator;
			this.discardUntil = discardUntil;
			this.levelAnalyzer = levelAnalyzer;
			this.articulationDetector = articulationDetector;
		}
	}

	/** A hop listener and the flag that tells the analysis thread to call its configure before its next hop. */
	private static final class HopEntry {
		final HopListener listener;
		volatile boolean needsConfigure = true;

		HopEntry(HopListener listener) {
			this.listener = listener;
		}
	}

	private final TGContext context;
	private final SampleRingBuffer ringBuffer;
	/** The analysers of the latest requested configuration (used when only the rate or estimator changes). */
	private LevelAnalyzer requestedLevelAnalyzer;
	private ArticulationDetector requestedArticulationDetector;
	private final Object reconfigureLock = new Object();
	private final int windowSize;
	private final int hopSize;
	private final List<Listener> listeners;
	/**
	 * The hop listeners, as a snapshot array replaced on every add and remove and walked by index by the analysis thread: no iterator, no lock and no
	 * allocation per hop (spec 010, constitution VI). Each entry remembers whether it still has to be told the sample rate.
	 */
	private volatile HopEntry[] hopListeners = new HopEntry[0];
	private final Object hopListenersLock = new Object();
	private final AtomicReference<Configuration> pendingConfiguration;
	private Configuration configuration;
	private volatile boolean spectrumEnabled;
	private PolyphonicAnalyzer polyphonicAnalyzer;
	/** Applied to every hop before the analysis (gain, noise reduction); neutral unless set (spec 002). */
	private final FrontEnd frontEnd = new FrontEnd();
	private volatile int[] expectedPitches;
	private volatile int[] attackCandidates;
	private AttackAnalyzer attackAnalyzer;
	private int[] attackAnalyzerCandidates;
	private int attackAnalyzerRate;
	private ExpectedPitchVerifier verifier;
	private int verifierRate;
	private boolean running;
	private boolean workerAlive;

	public AnalysisService(TGContext context, SampleRingBuffer ringBuffer, LevelAnalyzer levelAnalyzer,
			PitchEstimator pitchEstimator, int sampleRate, int windowSize, int hopSize) {
		this(context, ringBuffer, levelAnalyzer, new ArticulationDetector(), pitchEstimator, sampleRate, windowSize, hopSize);
	}

	public AnalysisService(TGContext context, SampleRingBuffer ringBuffer, LevelAnalyzer levelAnalyzer,
			ArticulationDetector articulationDetector, PitchEstimator pitchEstimator, int sampleRate,
			int windowSize, int hopSize) {
		if (sampleRate <= 0 || windowSize <= 0 || hopSize <= 0 || hopSize > windowSize || windowSize % hopSize != 0) {
			throw new IllegalArgumentException("invalid analysis dimensions");
		}
		this.context = context;
		this.ringBuffer = ringBuffer;
		this.requestedLevelAnalyzer = levelAnalyzer;
		this.requestedArticulationDetector = articulationDetector;
		this.windowSize = windowSize;
		this.hopSize = hopSize;
		this.configuration = new Configuration(sampleRate, pitchEstimator, 0L, levelAnalyzer, articulationDetector);
		this.pendingConfiguration = new AtomicReference<Configuration>();
		this.listeners = new CopyOnWriteArrayList<Listener>();
	}

	/** The input front end (gain, noise reduction) applied before the analysis; neutral by default. */
	public FrontEnd getFrontEnd() {
		return this.frontEnd;
	}

	public synchronized void subscribe(Listener listener) {
		this.listeners.add(listener);
		this.running = true;
		if (!this.workerAlive) {
			this.workerAlive = true;
			TGThreadManager.getInstance(this.context).start(TGThreadPriority.HIGH, this::run);
		}
	}

	/**
	 * Gives every hop of the signal the analysis sees (after the front end) to the listener, on the analysis thread, after a {@code configure(sampleRate)}
	 * that comes before its first hop, at every reconfiguration and when it is added while the engine runs (spec 010). A frame listener must be subscribed
	 * for the analysis to run. Adding is safe from any thread.
	 */
	public void addHopListener(HopListener listener) {
		synchronized (this.hopListenersLock) {
			HopEntry[] current = this.hopListeners;
			HopEntry[] next = java.util.Arrays.copyOf(current, current.length + 1);
			next[current.length] = new HopEntry(listener);
			this.hopListeners = next;
		}
	}

	public void removeHopListener(HopListener listener) {
		synchronized (this.hopListenersLock) {
			HopEntry[] current = this.hopListeners;
			List<HopEntry> kept = new java.util.ArrayList<HopEntry>(current.length);
			for (HopEntry entry : current) {
				if (entry.listener != listener) {
					kept.add(entry);
				}
			}
			this.hopListeners = kept.toArray(new HopEntry[0]);
		}
	}

	public synchronized void unsubscribe(Listener listener) {
		this.listeners.remove(listener);
		if (this.listeners.isEmpty()) {
			this.running = false;
		}
	}

	/**
	 * Switches analysis to a new sample rate and estimator, for example after capture opened
	 * the input at its negotiated rate. Samples written to the ring buffer before
	 * {@code discardUntil} (a {@link SampleRingBuffer#getWrittenSampleCount()} value) belong
	 * to the previous configuration and are discarded instead of analysed.
	 */
	public void reconfigure(int sampleRate, PitchEstimator pitchEstimator, long discardUntil) {
		synchronized (this.reconfigureLock) {
			reconfigure(sampleRate, pitchEstimator, discardUntil, this.requestedLevelAnalyzer, this.requestedArticulationDetector);
		}
	}

	/**
	 * Like {@link #reconfigure(int, PitchEstimator, long)}, also replacing the level analyser and
	 * the articulation detector (new settings, task T102). The worker switches everything at once
	 * and restarts its window from {@code discardUntil}: frames are never computed with a mix of
	 * old and new settings. The caller allocates; the worker only swaps a reference.
	 */
	public void reconfigure(int sampleRate, PitchEstimator pitchEstimator, long discardUntil,
			LevelAnalyzer levelAnalyzer, ArticulationDetector articulationDetector) {
		if (sampleRate <= 0) {
			throw new IllegalArgumentException("invalid sample rate");
		}
		if (pitchEstimator == null || levelAnalyzer == null || articulationDetector == null) {
			throw new IllegalArgumentException("estimator and analysers are required");
		}
		synchronized (this.reconfigureLock) {
			this.requestedLevelAnalyzer = levelAnalyzer;
			this.requestedArticulationDetector = articulationDetector;
			this.pendingConfiguration.set(new Configuration(sampleRate, pitchEstimator, discardUntil, levelAnalyzer, articulationDetector));
		}
	}

	/** The level analyser of the latest configuration handed to the worker (diagnostics, tests). */
	public LevelAnalyzer getRequestedLevelAnalyzer() {
		synchronized (this.reconfigureLock) {
			return this.requestedLevelAnalyzer;
		}
	}

	/** The articulation detector of the latest configuration handed to the worker (diagnostics, tests). */
	public ArticulationDetector getRequestedArticulationDetector() {
		synchronized (this.reconfigureLock) {
			return this.requestedArticulationDetector;
		}
	}

	/**
	 * Adds the polyphonic spectrum ({@link AnalysisFrame#getSpectrum()}) to every frame while
	 * enabled; it starts from an empty history when enabled. Single-note fields are unaffected.
	 */
	public void setSpectrumEnabled(boolean enabled) {
		this.spectrumEnabled = enabled;
	}

	/**
	 * The pitches the learner is expected to play now (null or empty: none). While set, every frame
	 * carries the {@link TargetEvidence} for them. Read by the analysis worker; never waits.
	 */
	public void setExpectedPitches(int[] midi) {
		this.expectedPitches = (midi == null || midi.length == 0 ? null : midi.clone());
	}

	/**
	 * The pitches the attack analysis explains the sound with (null or empty: off). While set, every
	 * frame carries the {@link AttackEvidence}; the practice window passes the pitches of the track
	 * and their neighbours (spec 004).
	 */
	public void setAttackCandidates(int[] pitches) {
		this.attackCandidates = (pitches == null || pitches.length == 0 ? null : pitches.clone());
	}

	private AttackEvidence pushAttack(float[] hop) {
		int[] candidates = this.attackCandidates;
		if( candidates == null ) {
			this.attackAnalyzer = null;
			return null;
		}
		if( this.attackAnalyzer == null || this.attackAnalyzerCandidates != candidates || this.attackAnalyzerRate != this.configuration.sampleRate ) {
			this.attackAnalyzer = new AttackAnalyzer(AttackAnalyzer.Parameters.defaults(), this.configuration.sampleRate, this.hopSize, candidates);
			this.attackAnalyzerCandidates = candidates;
			this.attackAnalyzerRate = this.configuration.sampleRate;
		}
		this.attackAnalyzer.push(hop, 0, this.hopSize);
		return this.attackAnalyzer.analyze();
	}

	private void run() {
		float[] window = new float[this.windowSize];
		float[] hop = new float[this.hopSize];
		int windowFill = 0;
		int hopFill = 0;
		long baseNanos = 0;
		long processedSamples = 0;
		long observedDroppedSamples = this.ringBuffer.getDroppedSampleCount();

		while (keepRunning()) {
			Configuration pending = this.pendingConfiguration.getAndSet(null);
			if (pending != null) {
				baseNanos += processedSamples * 1_000_000_000L / this.configuration.sampleRate;
				processedSamples = 0;
				this.configuration = pending;
				this.ringBuffer.skipTo(pending.discardUntil);
				observedDroppedSamples = this.ringBuffer.getDroppedSampleCount();
				this.configuration.articulationDetector.reset();
				this.polyphonicAnalyzer = null;
				this.attackAnalyzer = null;
				this.frontEnd.configure(this.configuration.sampleRate, this.hopSize);
				for (HopEntry entry : this.hopListeners) {
					entry.needsConfigure = true;
				}
				windowFill = 0;
				hopFill = 0;
			}
			long droppedSamples = this.ringBuffer.getDroppedSampleCount();
			if (droppedSamples != observedDroppedSamples) {
				processedSamples += droppedSamples - observedDroppedSamples;
				observedDroppedSamples = droppedSamples;
				this.configuration.articulationDetector.reset();
				this.frontEnd.reset();
				if (this.polyphonicAnalyzer != null) {
					this.polyphonicAnalyzer.reset();
				}
				this.attackAnalyzer = null;
				windowFill = 0;
				hopFill = 0;
			}
			int read = this.ringBuffer.read(hop, hopFill, this.hopSize - hopFill);
			if (read == 0) {
				pauseBriefly();
				continue;
			}
			hopFill += read;
			if (hopFill < this.hopSize) {
				continue;
			}
			// gain and noise reduction come first: the level, the pitch, the attack and the spectrum all see the same signal
			this.frontEnd.process(hop, this.hopSize);
			deliverHop(hop);

			if (windowFill < this.windowSize) {
				int copy = Math.min(this.hopSize, this.windowSize - windowFill);
				System.arraycopy(hop, 0, window, windowFill, copy);
				windowFill += copy;
			} else {
				System.arraycopy(window, this.hopSize, window, 0, this.windowSize - this.hopSize);
				System.arraycopy(hop, 0, window, this.windowSize - this.hopSize, this.hopSize);
			}
			processedSamples += this.hopSize;
			hopFill = 0;
			pushSpectrum(hop);
			AttackEvidence attack = pushAttack(hop);
			if (windowFill == this.windowSize) {
				long timestampNanos = baseNanos + processedSamples * 1_000_000_000L / this.configuration.sampleRate;
				publish(analyze(window, timestampNanos, attack, this.ringBuffer.getReadSampleCount()));
			}
		}
	}

	/** One hop to every hop listener, each told the sample rate first when it has not been yet; a listener that throws is skipped (the analysis goes on). */
	private void deliverHop(float[] hop) {
		HopEntry[] entries = this.hopListeners;
		for (int i = 0; i < entries.length; i++) {
			HopEntry entry = entries[i];
			try {
				if (entry.needsConfigure) {
					entry.needsConfigure = false;
					entry.listener.configure(this.configuration.sampleRate);
				}
				entry.listener.hop(hop, 0, this.hopSize);
			} catch (RuntimeException exception) {
				// a bad listener does not stop the analysis nor the other listeners
			}
		}
	}

	private void pushSpectrum(float[] hop) {
		if (!this.spectrumEnabled) {
			this.polyphonicAnalyzer = null;
			return;
		}
		if (this.polyphonicAnalyzer == null) {
			this.polyphonicAnalyzer = new PolyphonicAnalyzer(PolyphonicAnalyzer.Parameters.defaults(), this.configuration.sampleRate);
		}
		this.polyphonicAnalyzer.push(hop, 0, hop.length);
	}

	private AnalysisFrame analyze(float[] window, long timestampNanos, AttackEvidence attack, long captureSampleEnd) {
		LevelMeasurement level = this.configuration.levelAnalyzer.analyze(window);
		PitchEstimate pitch = this.configuration.pitchEstimator.estimate(window);
		boolean onset = this.configuration.articulationDetector.process(window);
		SpectrumFrame spectrum = (this.polyphonicAnalyzer != null ? this.polyphonicAnalyzer.analyze() : null);
		int[] expected = this.expectedPitches;
		TargetEvidence evidence = null;
		if( expected != null ) {
			if( this.verifier == null || this.verifierRate != this.configuration.sampleRate ) {
				this.verifier = new ExpectedPitchVerifier(this.configuration.sampleRate);
				this.verifierRate = this.configuration.sampleRate;
			}
			evidence = this.verifier.verify(window, expected);
		}
		return new AnalysisFrame(timestampNanos, level.getRmsDbfs(), level.getPeakDbfs(),
				level.getClippedRatio(), level.getLevelState(),
				pitch.isPitched() ? pitch.getF0Hz() : Double.NaN,
				pitch.getProbability(), onset, spectrum, evidence, null, attack, captureSampleEnd);
	}

	private void publish(AnalysisFrame frame) {
		for (Listener listener : this.listeners) {
			try {
				listener.analysisFrame(frame);
			} catch (RuntimeException exception) {
				// Analysis continues even if one consumer rejects a frame.
			}
		}
	}

	private synchronized boolean keepRunning() {
		if (!this.running) {
			this.workerAlive = false;
		}
		return this.running;
	}

	private static void pauseBriefly() {
		try {
			Thread.sleep(1L);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
		}
	}
}
