package app.tuxguitar.audio.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.analysis.PitchEstimate;
import app.tuxguitar.audio.engine.analysis.PitchEstimator;
import app.tuxguitar.audio.engine.backend.FakeRecognitionApi;
import app.tuxguitar.audio.engine.backend.NativeBackend;
import app.tuxguitar.audio.engine.backend.RecognitionBackendFactory;
import app.tuxguitar.audio.engine.input.CurrentInputSelection;
import app.tuxguitar.audio.engine.nativeengine.NativeParameters;
import app.tuxguitar.audio.engine.nativeengine.NativeRecognitionException;
import app.tuxguitar.audio.engine.nativeengine.NativeRecognitionLibrary;
import app.tuxguitar.audio.engine.settings.PitchEngineCatalog;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.audio.engine.settings.RecognitionSettingsStore;
import app.tuxguitar.thread.TGMultiThreadHandler;
import app.tuxguitar.thread.TGThreadManager;
import app.tuxguitar.util.TGContext;

/**
 * The native engine of a run gets the tuning array at open and again when the settings change (spec 026, T007), as it gets the {@code float[21]}
 * today (spec 023, {@code AudioEngineBackendTest.settingsAndGainChangesReachTheNativeEngineDuringARun}).
 */
public class AudioEngineTuningPushTest {

	private static final IntFunction<PitchEstimator> ESTIMATORS = rate -> window -> new PitchEstimate(220.0, 0.9, true);

	/** The fake of spec 023 (final, so wrapped) that also records the tuning it is given. */
	private static final class TuningApi implements NativeRecognitionLibrary.Api {
		final FakeRecognitionApi fake = new FakeRecognitionApi();
		volatile double[] openedTuning;
		volatile double[] configuredTuning;
		final AtomicInteger tuningPushes = new AtomicInteger();

		@Override
		public long open(String inputName, int inputChannel, int blockFrames, long javaNanosAtOpen, float[] parameters, double[] tuning)
				throws NativeRecognitionException {
			this.openedTuning = tuning.clone();
			return this.fake.open(inputName, inputChannel, blockFrames, javaNanosAtOpen, parameters);
		}

		@Override
		public void configureTuning(long handle, double[] tuning) {
			this.configuredTuning = tuning.clone();
			this.tuningPushes.incrementAndGet();
		}

		public long open(String inputName, int inputChannel, int blockFrames, long javaNanosAtOpen, float[] parameters) throws NativeRecognitionException {
			return this.fake.open(inputName, inputChannel, blockFrames, javaNanosAtOpen, parameters);
		}

		public float sampleRate(long handle) {
			return this.fake.sampleRate(handle);
		}

		public void configure(long handle, float[] parameters) {
			this.fake.configure(handle, parameters);
		}

		public void setSpectrumEnabled(long handle, boolean on) {
			this.fake.setSpectrumEnabled(handle, on);
		}

		public void setExpectedPitches(long handle, int[] midi) {
			this.fake.setExpectedPitches(handle, midi);
		}

		public void setExpectedChord(long handle, int[] required, int[] tolerated) {
			this.fake.setExpectedChord(handle, required, tolerated);
		}

		public void setAttackCandidates(long handle, int[] pitches) {
			this.fake.setAttackCandidates(handle, pitches);
		}

		public int nextFrames(long handle, float[] records, int timeoutMillis) {
			return this.fake.nextFrames(handle, records, timeoutMillis);
		}

		public int nextHops(long handle, float[] hops, int timeoutMillis) {
			return this.fake.nextHops(handle, hops, timeoutMillis);
		}

		public void setHopsEnabled(long handle, boolean on) {
			this.fake.setHopsEnabled(handle, on);
		}

		public long droppedBlocks(long handle) {
			return this.fake.droppedBlocks(handle);
		}

		public void close(long handle) {
			this.fake.close(handle);
		}

		public float[] strumClip(long handle) {
			return this.fake.strumClip(handle);
		}

		public void resetStrumClip(long handle) {
			this.fake.resetStrumClip(handle);
		}

		public float[] stats(long handle) {
			return this.fake.stats(handle);
		}
	}

	private TGContext context;
	private RecognitionSettingsStore store;
	private TuningApi api;
	private AudioEngine engine;

	@BeforeEach
	public void setUp() {
		this.context = new TGContext();
		TGThreadManager.getInstance(this.context).setThreadHandler(new TGMultiThreadHandler());
		this.store = new RecognitionSettingsStore(new Memory());
		this.api = new TuningApi();
		RecognitionBackendFactory factory = new RecognitionBackendFactory() {
			@Override
			public PitchEngineCatalog.Kind resolveKind(RecognitionSettings settings) {
				return PitchEngineCatalog.Kind.NATIVE;
			}

			@Override
			public NativeBackend createNative(TGContext context, CurrentInputSelection selection, Supplier<float[]> parameters) {
				return new NativeBackend(context, selection, AudioEngineTuningPushTest.this.api, parameters);
			}
		};
		this.engine = BackendTestEngines.withBackends(this.context, this.store, ESTIMATORS, factory);
	}

	@AfterEach
	public void tearDown() {
		this.engine.release("a");
		TGThreadManager.getInstance(this.context).dispose();
	}

	@Test
	public void theTuningIsGivenAtOpen() {
		this.engine.acquire("a");
		assertEquals(NativeParameters.TUNING_LENGTH, this.api.openedTuning.length);
	}

	@Test
	public void aSettingsChangeDuringARunPushesTheTuning() throws Exception {
		this.engine.acquire("a");
		int before = this.api.tuningPushes.get();
		this.store.update(this.store.get().with(RecognitionParameters.INPUT_LEVEL_MIN, -30));
		waitFor(() -> this.api.tuningPushes.get() > before, 2000);
		assertEquals(NativeParameters.TUNING_LENGTH, this.api.configuredTuning.length);
	}

	private static void waitFor(BooleanSupplier condition, long millis) throws InterruptedException {
		long deadline = System.nanoTime() + millis * 1_000_000L;
		while( !condition.getAsBoolean() ) {
			if( System.nanoTime() > deadline ) {
				throw new AssertionError("not reached within " + millis + " ms");
			}
			Thread.sleep(2L);
		}
	}

	private static final class Memory implements RecognitionSettingsStore.Persistence {
		private Map<String, String> values = new HashMap<String, String>();

		@Override
		public Map<String, String> read() {
			return new HashMap<String, String>(this.values);
		}

		@Override
		public void write(Map<String, String> values) {
			this.values = new HashMap<String, String>(values);
		}
	}
}
