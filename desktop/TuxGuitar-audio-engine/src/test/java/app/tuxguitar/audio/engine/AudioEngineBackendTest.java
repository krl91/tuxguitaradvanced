package app.tuxguitar.audio.engine;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.analysis.HopListener;
import app.tuxguitar.audio.engine.analysis.PitchEstimate;
import app.tuxguitar.audio.engine.analysis.PitchEstimator;
import app.tuxguitar.audio.engine.analysis.StrumVerdict;
import app.tuxguitar.audio.engine.backend.FakeRecognitionApi;
import app.tuxguitar.audio.engine.backend.NativeBackend;
import app.tuxguitar.audio.engine.backend.RecognitionBackendFactory;
import app.tuxguitar.audio.engine.input.AudioInputDescriptor;
import app.tuxguitar.audio.engine.input.CaptureService;
import app.tuxguitar.audio.engine.input.CurrentInputSelection;
import app.tuxguitar.audio.engine.nativeengine.NativeParameters;
import app.tuxguitar.audio.engine.settings.PitchEngineCatalog;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.audio.engine.settings.RecognitionSettingsStore;
import app.tuxguitar.thread.TGMultiThreadHandler;
import app.tuxguitar.thread.TGThreadManager;
import app.tuxguitar.util.TGContext;

/** The engine chooses its recognition backend at the first acquire and keeps what its consumers set across backends (spec 023, T043, data-model "Backend"). */
public class AudioEngineBackendTest {

	private static final IntFunction<PitchEstimator> ESTIMATORS = rate -> window -> new PitchEstimate(220.0, 0.9, true);

	private TGContext context;
	private RecognitionSettingsStore store;
	private FakeRecognitionApi api;
	private final AtomicReference<PitchEngineCatalog.Kind> kind = new AtomicReference<PitchEngineCatalog.Kind>(PitchEngineCatalog.Kind.NATIVE);
	private final AtomicInteger nativeBackends = new AtomicInteger();
	private AudioEngine engine;

	@BeforeEach
	public void setUp() {
		this.context = new TGContext();
		TGThreadManager.getInstance(this.context).setThreadHandler(new TGMultiThreadHandler());
		this.store = new RecognitionSettingsStore(new Memory());
		this.api = new FakeRecognitionApi();
		RecognitionBackendFactory factory = new RecognitionBackendFactory() {
			@Override
			public PitchEngineCatalog.Kind resolveKind(RecognitionSettings settings) {
				return AudioEngineBackendTest.this.kind.get();
			}

			@Override
			public NativeBackend createNative(TGContext context, CurrentInputSelection selection, Supplier<float[]> parameters) {
				AudioEngineBackendTest.this.nativeBackends.incrementAndGet();
				return new NativeBackend(context, selection, AudioEngineBackendTest.this.api, parameters);
			}
		};
		this.engine = BackendTestEngines.withBackends(this.context, this.store, ESTIMATORS, factory);
	}

	@AfterEach
	public void tearDown() {
		for( String consumer : new String[] { "a", "b", "c" } ) {
			this.engine.release(consumer);
		}
		TGThreadManager.getInstance(this.context).dispose();
	}

	@Test
	public void theBackendIsChosenAtTheFirstAcquireFromTheResolvedKind() {
		assertEquals(PitchEngineCatalog.Kind.JAVA, this.engine.getActiveBackend().engineKind(), "nothing is opened before the first acquire");
		assertEquals(0, this.api.opens.get());
		this.engine.acquire("a");
		assertEquals(PitchEngineCatalog.Kind.NATIVE, this.engine.getActiveBackend().engineKind());
		assertEquals(1, this.api.opens.get());
	}

	@Test
	public void aChangedChoiceAppliesAtTheNextAcquireWhenNoConsumerHoldsTheCapture() {
		this.engine.acquire("a");
		this.kind.set(PitchEngineCatalog.Kind.JAVA);
		this.engine.acquire("b");
		assertEquals(PitchEngineCatalog.Kind.NATIVE, this.engine.getActiveBackend().engineKind(), "never during a run");
		this.engine.release("a");
		this.engine.release("b");
		assertTrue(!this.api.isOpen());
		this.engine.acquire("c");
		assertEquals(PitchEngineCatalog.Kind.JAVA, this.engine.getActiveBackend().engineKind());
	}

	@Test
	public void whatTheConsumersSetIsReappliedToABackendThatOpensLater() throws Exception {
		List<AnalysisFrame> frames = new CopyOnWriteArrayList<AnalysisFrame>();
		List<CaptureService.EventType> events = new CopyOnWriteArrayList<CaptureService.EventType>();
		List<String> hops = new CopyOnWriteArrayList<String>();
		this.engine.subscribe(frames::add);
		this.engine.addCaptureListener(event -> events.add(event.getType()));
		this.engine.addHopListener(new HopListener() {
			@Override
			public void configure(int sampleRate) {
				hops.add("configure " + sampleRate);
			}

			@Override
			public void hop(float[] samples, int offset, int length) {
				hops.add("hop");
			}
		});
		this.engine.setSpectrumEnabled(true);
		this.engine.setExpectedPitches(new int[] { 52 });
		this.engine.setExpectedChord(new int[] { 40, 47, 52 }, new int[] { 45 });
		this.engine.setAttackCandidates(new int[] { 40, 47, 52, 56 });
		double gain = this.engine.setInputGain(2.0);

		this.engine.acquire("a");
		assertTrue(this.api.spectrum);
		assertArrayEquals(new int[] { 52 }, this.api.expected);
		assertArrayEquals(new int[] { 40, 47, 52 }, this.api.chordRequired);
		assertArrayEquals(new int[] { 45 }, this.api.chordTolerated);
		assertArrayEquals(new int[] { 40, 47, 52, 56 }, this.api.attack);
		assertEquals(gain, this.api.openedParameters[NativeParameters.FRONT_GAIN], 1e-6, "the front end's gain");
		waitFor(() -> this.api.hopsEnabled, 2000);
		this.api.pushHop(new float[256]);
		this.api.push(FakeRecognitionApi.frame(10_000_000L, 2048), 1L);
		waitFor(() -> frames.size() == 1 && hops.size() == 2 && events.contains(CaptureService.EventType.STARTED), 2000);
		assertEquals("configure 48000", hops.get(0));
	}

	@Test
	public void settingsAndGainChangesReachTheNativeEngineDuringARun() throws Exception {
		this.engine.acquire("a");
		this.store.update(this.store.get().with(RecognitionParameters.INPUT_LEVEL_MIN, -30));
		waitFor(() -> this.api.configured != null && this.api.configured[NativeParameters.LEVEL_TOO_LOW] == -30f, 2000);
		this.engine.setInputGain(1.5);
		assertEquals(1.5f, this.api.configured[NativeParameters.FRONT_GAIN], 1e-6);
	}

	@Test
	public void thePackageConstructorsKeepTheJavaBackend() {
		AudioEngine plain = BackendTestEngines.withEstimator(this.context, ESTIMATORS);
		try {
			plain.acquire("a");
			assertEquals(PitchEngineCatalog.Kind.JAVA, plain.getActiveBackend().engineKind());
			assertEquals(1, plain.getJavaCaptureConsumerCount());
		} finally {
			plain.release("a");
		}
	}

	@Test
	public void theChosenDeviceAndChannelReachTheOpenUnchanged() {
		this.engine.getSelection().select(new AudioInputDescriptor("usb", "Rocksmith USB Guitar Adapter", 0, 1, "Rocksmith", AudioInputDescriptor.Kind.INSTRUMENT, false), null);
		this.engine.acquire("a");
		assertEquals("Rocksmith USB Guitar Adapter", this.api.openedName);
		assertEquals(0, this.api.openedChannel);
	}

	@Test
	public void withTheNativeEngineNoJavaCaptureIsEverOpenedNotEvenForTheMeterOrThePreRunCheck() throws Exception {
		this.engine.getSelection().select(new AudioInputDescriptor("mic", "MacBook Pro Microphone", null, 1, "Built-in", AudioInputDescriptor.Kind.BUILT_IN_MICROPHONE, true), null);
		this.engine.acquire("a");
		// the input meter and the pre-run check subscribe and add hop listeners on the held capture; the level check of a new input too
		List<AnalysisFrame> meter = new CopyOnWriteArrayList<AnalysisFrame>();
		this.engine.subscribe(meter::add);
		this.engine.runLevelCheck();
		this.engine.acquire("b");
		this.api.push(FakeRecognitionApi.frame(10_000_000L, 2048), 1L);
		waitFor(() -> meter.size() == 1, 2000);
		assertEquals(0, this.engine.getJavaCaptureConsumerCount());
		assertEquals(1, this.api.opens.get(), "one native open for every consumer");
		assertEquals(1, this.nativeBackends.get());
	}

	@Test
	public void theStrumReadingIsTheNativeOneOnTheNativeBackendOnly() {
		assertNull(this.engine.nativeStrumReading(), "Java: the pre-run check keeps its own meter");
		this.engine.acquire("a");
		this.api.strum = new float[] { StrumVerdict.GOOD.ordinal(), 0f, -18f };
		assertNotNull(this.engine.nativeStrumReading());
		assertEquals(StrumVerdict.GOOD, this.engine.nativeStrumReading().verdict());
		this.engine.resetNativeStrumReading();
		assertEquals(1, this.api.strumResets.get());
	}

	@Test
	public void theDropsOfTheNativeCaptureAreCounted() throws Exception {
		List<AnalysisFrame> frames = new CopyOnWriteArrayList<AnalysisFrame>();
		this.engine.subscribe(frames::add);
		this.engine.acquire("a");
		this.api.dropped = 2;
		this.api.push(FakeRecognitionApi.frame(10_000_000L, 2048), 1L);
		waitFor(() -> frames.size() == 1, 2000);
		assertEquals(256L, this.engine.getDroppedSampleCount());
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
