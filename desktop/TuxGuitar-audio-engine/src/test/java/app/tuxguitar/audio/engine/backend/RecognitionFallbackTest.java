package app.tuxguitar.audio.engine.backend;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.AudioEngine;
import app.tuxguitar.audio.engine.BackendTestEngines;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.analysis.PitchEstimate;
import app.tuxguitar.audio.engine.analysis.PitchEstimator;
import app.tuxguitar.audio.engine.input.CaptureService;
import app.tuxguitar.audio.engine.input.CurrentInputSelection;
import app.tuxguitar.audio.engine.input.SampleRingBuffer;
import app.tuxguitar.audio.engine.nativeengine.NativeRecognitionException;
import app.tuxguitar.audio.engine.nativeengine.NativeRecognitionException.Reason;
import app.tuxguitar.audio.engine.nativeengine.NativeRecognitionLibrary;
import app.tuxguitar.audio.engine.settings.PitchEngineCatalog;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.audio.engine.settings.RecognitionSettingsStore;
import app.tuxguitar.thread.TGMultiThreadHandler;
import app.tuxguitar.thread.TGThreadManager;
import app.tuxguitar.util.TGContext;

/**
 * A native engine that cannot start falls back to Java (spec 023, T060, FR-006, SC-003, data-model "Recognition engine notice"): the Java backend runs,
 * one notice per run says why, the run's first frame comes within 2 s, an open that never returns is abandoned after 1.5 s, and no exception reaches
 * the caller. A refused microphone permission is not a fallback: the capture error reaches the listeners as with the Java capture, and no notice is sent.
 */
public class RecognitionFallbackTest {

	private static final IntFunction<PitchEstimator> ESTIMATORS = rate -> window -> new PitchEstimate(220.0, 0.9, true);

	private TGContext context;
	private FakeRecognitionApi api;
	private GatedApi gated;
	private final AtomicReference<PitchEngineCatalog.Kind> kind = new AtomicReference<PitchEngineCatalog.Kind>(PitchEngineCatalog.Kind.NATIVE);
	private final AtomicReference<String> libraryFailure = new AtomicReference<String>();
	private final List<RecognitionEngineNotice> notices = new CopyOnWriteArrayList<RecognitionEngineNotice>();
	private final List<CaptureService.EventType> events = new CopyOnWriteArrayList<CaptureService.EventType>();
	private final List<AnalysisFrame> frames = new CopyOnWriteArrayList<AnalysisFrame>();
	private AudioEngine engine;

	@BeforeEach
	public void setUp() {
		this.context = new TGContext();
		TGThreadManager.getInstance(this.context).setThreadHandler(new TGMultiThreadHandler());
		this.api = new FakeRecognitionApi();
		this.gated = new GatedApi(this.api);
		RecognitionBackendFactory factory = new RecognitionBackendFactory() {
			@Override
			public PitchEngineCatalog.Kind resolveKind(RecognitionSettings settings) {
				return RecognitionFallbackTest.this.kind.get();
			}

			@Override
			public String nativeLibraryFailure(RecognitionSettings settings) {
				return RecognitionFallbackTest.this.libraryFailure.get();
			}

			@Override
			public NativeBackend createNative(TGContext context, CurrentInputSelection selection, Supplier<float[]> parameters) {
				return new NativeBackend(context, selection, RecognitionFallbackTest.this.gated, parameters);
			}
		};
		this.engine = BackendTestEngines.withBackends(this.context, new RecognitionSettingsStore(new Memory()), ESTIMATORS, factory);
		this.engine.addRecognitionEngineNoticeListener(this.notices::add);
		this.engine.addCaptureListener(event -> this.events.add(event.getType()));
		this.engine.subscribe(this.frames::add);
	}

	@AfterEach
	public void tearDown() {
		for( String consumer : new String[] { "a", "b" } ) {
			this.engine.release(consumer);
		}
		TGThreadManager.getInstance(this.context).dispose();
	}

	@Test
	public void eachOpenFailureFallsBackToJavaWithOneNotice() throws Exception {
		for( Reason reason : new Reason[] { Reason.LIBRARY_MISSING, Reason.DEVICE_NOT_FOUND, Reason.FORMAT_UNSUPPORTED, Reason.STREAM_FAILED } ) {
			this.notices.clear();
			this.frames.clear();
			this.api.openFailure = new NativeRecognitionException(reason, "detail of " + reason);
			long started = System.nanoTime();
			assertDoesNotThrow(() -> this.engine.acquire("a"));
			assertDoesNotThrow(() -> this.engine.acquire("b"));
			assertEquals(PitchEngineCatalog.Kind.JAVA, BackendTestEngines.activeBackendOf(this.engine).engineKind(), reason.name());
			assertEquals(1, BackendTestEngines.javaCaptureConsumersOf(this.engine) > 0 ? 1 : 0, reason + ": the Java capture runs");
			assertEquals(1, this.notices.size(), reason + ": one notice per run, " + this.notices);
			assertEquals(reason, this.notices.get(0).getReason());
			assertEquals("detail of " + reason, this.notices.get(0).getDetail());
			firstFrameWithin(started, 2000, reason.name());
			this.engine.release("a");
			this.engine.release("b");
		}
	}

	@Test
	public void aLibraryThatDoesNotLoadFallsBackToJavaWithOneNotice() throws Exception {
		this.kind.set(PitchEngineCatalog.Kind.JAVA);
		this.libraryFailure.set("no library here");
		long started = System.nanoTime();
		assertDoesNotThrow(() -> this.engine.acquire("a"));
		assertEquals(PitchEngineCatalog.Kind.JAVA, BackendTestEngines.activeBackendOf(this.engine).engineKind());
		assertEquals(1, this.notices.size(), String.valueOf(this.notices));
		assertEquals(Reason.LIBRARY_MISSING, this.notices.get(0).getReason());
		assertEquals("no library here", this.notices.get(0).getDetail());
		assertEquals(0, this.api.opens.get(), "no open is tried without the library");
		firstFrameWithin(started, 2000, "library missing");
	}

	@Test
	public void aJavaChoiceSendsNoNotice() {
		this.kind.set(PitchEngineCatalog.Kind.JAVA);
		this.engine.acquire("a");
		assertTrue(this.notices.isEmpty(), String.valueOf(this.notices));
	}

	@Test
	public void eachRunThatFallsBackSendsItsNotice() {
		this.api.openFailure = new NativeRecognitionException(Reason.DEVICE_NOT_FOUND, "gone");
		this.engine.acquire("a");
		this.engine.release("a");
		this.engine.acquire("a");
		assertEquals(2, this.notices.size(), String.valueOf(this.notices));
	}

	@Test
	public void anOpenThatNeverReturnsIsAbandonedAfterOneAndAHalfSeconds() throws Exception {
		CountDownLatch held = new CountDownLatch(1);
		this.gated.gate = held;
		long started = System.nanoTime();
		assertDoesNotThrow(() -> this.engine.acquire("a"));
		long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
		assertTrue(millis >= 1400 && millis < 1900, "acquire returned after " + millis + " ms");
		assertEquals(PitchEngineCatalog.Kind.JAVA, BackendTestEngines.activeBackendOf(this.engine).engineKind());
		assertEquals(1, this.notices.size(), String.valueOf(this.notices));
		assertEquals(Reason.STREAM_FAILED, this.notices.get(0).getReason());
		firstFrameWithin(started, 2000, "abandoned open");
		// the open returns at last: the stream it opened is closed, the run stays on Java
		held.countDown();
		waitFor(() -> this.api.closes.get() == 1 && !this.api.isOpen(), 2000);
		assertEquals(PitchEngineCatalog.Kind.JAVA, BackendTestEngines.activeBackendOf(this.engine).engineKind());
	}

	@Test
	public void aRefusedPermissionIsNotAFallback() throws Exception {
		this.api.openFailure = new NativeRecognitionException(Reason.PERMISSION_DENIED, "the user said no");
		assertDoesNotThrow(() -> this.engine.acquire("a"));
		waitFor(() -> this.events.contains(CaptureService.EventType.CAPTURE_ERROR), 2000);
		assertEquals(1, count(CaptureService.EventType.CAPTURE_ERROR), String.valueOf(this.events));
		assertTrue(this.notices.isEmpty(), String.valueOf(this.notices));
		assertEquals(0, BackendTestEngines.javaCaptureConsumersOf(this.engine), "no Java capture is tried");
		assertEquals(1, this.api.opens.get(), "one open only");
	}

	/** Feeds the Java analysis and waits for its first frame, within {@code millis} of the acquire. */
	private void firstFrameWithin(long started, long millis, String what) throws InterruptedException {
		SampleRingBuffer ring = BackendTestEngines.ringOf(this.engine);
		float[] block = new float[4096];
		for( int i = 0 ; i < block.length ; i ++ ) {
			block[i] = (float) (0.3 * Math.sin(2 * Math.PI * 220.0 * i / 48000.0));
		}
		long deadline = started + TimeUnit.MILLISECONDS.toNanos(millis);
		while( this.frames.isEmpty() && System.nanoTime() < deadline ) {
			ring.write(block, 0, Math.min(block.length, ring.remainingCapacity()));
			Thread.sleep(5L);
		}
		assertTrue(!this.frames.isEmpty(), what + ": no frame within " + millis + " ms of the acquire");
	}

	private int count(CaptureService.EventType type) {
		int n = 0;
		for( CaptureService.EventType event : this.events ) {
			n += (event == type ? 1 : 0);
		}
		return n;
	}

	private static void waitFor(BooleanSupplier condition, long millis) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
		while( !condition.getAsBoolean() && System.nanoTime() < deadline ) {
			Thread.sleep(5L);
		}
		assertTrue(condition.getAsBoolean(), "condition not met within " + millis + " ms");
	}

	/** The fake library, whose open can be held until the test lets it return. */
	private static final class GatedApi implements NativeRecognitionLibrary.Api {
		final FakeRecognitionApi api;
		volatile CountDownLatch gate;

		GatedApi(FakeRecognitionApi api) {
			this.api = api;
		}

		@Override
		public long open(String inputName, int inputChannel, int blockFrames, long javaNanosAtOpen, float[] parameters) throws NativeRecognitionException {
			CountDownLatch held = this.gate;
			if( held != null ) {
				try {
					held.await();
				} catch (InterruptedException exception) {
					Thread.currentThread().interrupt();
				}
			}
			return this.api.open(inputName, inputChannel, blockFrames, javaNanosAtOpen, parameters);
		}

		@Override
		public float sampleRate(long handle) {
			return this.api.sampleRate(handle);
		}

		@Override
		public void configure(long handle, float[] parameters) {
			this.api.configure(handle, parameters);
		}

		@Override
		public void setSpectrumEnabled(long handle, boolean on) {
			this.api.setSpectrumEnabled(handle, on);
		}

		@Override
		public void setExpectedPitches(long handle, int[] midi) {
			this.api.setExpectedPitches(handle, midi);
		}

		@Override
		public void setExpectedChord(long handle, int[] required, int[] tolerated) {
			this.api.setExpectedChord(handle, required, tolerated);
		}

		@Override
		public void setAttackCandidates(long handle, int[] pitches) {
			this.api.setAttackCandidates(handle, pitches);
		}

		@Override
		public int nextFrames(long handle, float[] records, int timeoutMillis) {
			return this.api.nextFrames(handle, records, timeoutMillis);
		}

		@Override
		public int nextHops(long handle, float[] hops, int timeoutMillis) {
			return this.api.nextHops(handle, hops, timeoutMillis);
		}

		@Override
		public void setHopsEnabled(long handle, boolean on) {
			this.api.setHopsEnabled(handle, on);
		}

		@Override
		public long droppedBlocks(long handle) {
			return this.api.droppedBlocks(handle);
		}

		@Override
		public void close(long handle) {
			this.api.close(handle);
		}

		@Override
		public float[] strumClip(long handle) {
			return this.api.strumClip(handle);
		}

		@Override
		public void resetStrumClip(long handle) {
			this.api.resetStrumClip(handle);
		}

		@Override
		public float[] stats(long handle) {
			return this.api.stats(handle);
		}
	}

	private static final class Memory implements RecognitionSettingsStore.Persistence {
		private final Map<String, String> values = new HashMap<String, String>();

		@Override
		public Map<String, String> read() {
			return new HashMap<String, String>(this.values);
		}

		@Override
		public void write(Map<String, String> values) {
			this.values.clear();
			this.values.putAll(values);
		}
	}
}
