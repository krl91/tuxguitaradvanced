package app.tuxguitar.audio.engine.backend;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.analysis.HopListener;
import app.tuxguitar.audio.engine.input.AudioInputDescriptor;
import app.tuxguitar.audio.engine.input.CaptureService;
import app.tuxguitar.audio.engine.input.CurrentInputSelection;
import app.tuxguitar.audio.engine.nativeengine.NativeParameters;
import app.tuxguitar.audio.engine.settings.PitchEngineCatalog;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.thread.TGMultiThreadHandler;
import app.tuxguitar.thread.TGThreadManager;
import app.tuxguitar.util.TGContext;

/** The native backend over a fake library (spec 023, T042, contract §2): how the records and hop blocks of the native thread reach the Java listeners. */
public class NativeBackendTest {

	private static final int RATE = 48000;
	private static final long HOP_NANOS = 256L * 1_000_000_000L / RATE;

	private TGContext context;
	private CurrentInputSelection selection;
	private FakeRecognitionApi api;
	private NativeBackend backend;

	@BeforeEach
	public void setUp() {
		this.context = new TGContext();
		TGThreadManager.getInstance(this.context).setThreadHandler(new TGMultiThreadHandler());
		this.selection = CurrentInputSelection.getInstance(this.context);
		this.api = new FakeRecognitionApi();
		this.backend = new NativeBackend(this.context, this.selection, this.api, () -> NativeParameters.of(RecognitionSettings.defaults()));
	}

	@AfterEach
	public void tearDown() {
		this.backend.close();
		TGThreadManager.getInstance(this.context).dispose();
	}

	@Test
	public void itIsTheNativeEngine() {
		assertEquals(PitchEngineCatalog.Kind.NATIVE, this.backend.engineKind());
	}

	@Test
	public void theFramesOfTheRecordsArriveInOrderOnOneThreadThatIsNotTheCallers() throws Exception {
		List<AnalysisFrame> frames = new CopyOnWriteArrayList<AnalysisFrame>();
		List<Thread> threads = new CopyOnWriteArrayList<Thread>();
		this.backend.subscribe(frame -> {
			frames.add(frame);
			threads.add(Thread.currentThread());
		});
		this.backend.acquire("test");
		for( int i = 0; i < 50; i++ ) {
			this.api.push(FakeRecognitionApi.frame((8 + i) * HOP_NANOS, 2048 + 256L * i), 1_000_000L + i);
		}
		waitFor(() -> frames.size() == 50, 2000);
		for( int i = 0; i < 50; i++ ) {
			assertEquals((8 + i) * HOP_NANOS, frames.get(i).getTimestampNanos());
			assertEquals(2048 + 256L * i, frames.get(i).getCaptureSampleEnd());
			assertEquals(threads.get(0), threads.get(i), "one thread");
		}
		assertNotSame(Thread.currentThread(), threads.get(0));
	}

	@Test
	public void theCaptureClockIsFedFromTheSampleEndAndTheArrivalOfTheRecords() throws Exception {
		List<AnalysisFrame> frames = new CopyOnWriteArrayList<AnalysisFrame>();
		this.backend.subscribe(frames::add);
		this.backend.acquire("test");
		long base = 5_000_000_000L;
		long lag = 3_000_000L;
		for( int i = 0; i < 40; i++ ) {
			long end = 2048 + 256L * i;
			this.api.push(FakeRecognitionApi.frame((8 + i) * HOP_NANOS, end), base + end * 1_000_000_000L / RATE + lag);
		}
		waitFor(() -> frames.size() == 40, 2000);
		assertTrue(this.backend.clock().isReady());
		assertEquals(base + lag + 4096L * 1_000_000_000L / RATE, this.backend.clock().wallNanosOf(4096));
	}

	@Test
	public void aStoppedStreamIsTheInputLostEventThePracticeAlreadyHandles() throws Exception {
		List<CaptureService.EventType> events = new CopyOnWriteArrayList<CaptureService.EventType>();
		this.backend.addCaptureListener(event -> events.add(event.getType()));
		this.backend.acquire("test");
		waitFor(() -> events.contains(CaptureService.EventType.STARTED), 2000);
		this.api.fail();
		waitFor(() -> events.contains(CaptureService.EventType.DEVICE_LOST), 2000);
	}

	@Test
	public void droppedBlocksAreCountedInSamplesAndRestartTheClock() throws Exception {
		List<AnalysisFrame> frames = new CopyOnWriteArrayList<AnalysisFrame>();
		this.backend.subscribe(frames::add);
		this.backend.acquire("test");
		for( int i = 0; i < 30; i++ ) {
			this.api.push(FakeRecognitionApi.frame((8 + i) * HOP_NANOS, 2048 + 256L * i), 1_000_000L * i);
		}
		waitFor(() -> frames.size() == 30, 2000);
		assertTrue(this.backend.clock().isReady());
		this.api.dropped = 3;
		this.api.push(FakeRecognitionApi.frame(40 * HOP_NANOS, 2048 + 256L * 40), 50_000_000L);
		waitFor(() -> frames.size() == 31, 2000);
		assertEquals(3L * 128L, this.backend.getDroppedSampleCount());
		assertFalse(this.backend.clock().isReady(), "the index no longer follows time: the estimate starts again");
	}

	@Test
	public void hopListenersGetTheHopBlocksOnlyWhileRegisteredAfterTheirRate() throws Exception {
		List<String> heard = new CopyOnWriteArrayList<String>();
		HopListener listener = new HopListener() {
			@Override
			public void configure(int sampleRate) {
				heard.add("configure " + sampleRate);
			}

			@Override
			public void hop(float[] samples, int offset, int length) {
				heard.add("hop " + samples[offset] + " " + length);
			}
		};
		this.backend.acquire("test");
		assertFalse(this.api.hopsEnabled, "no hop listener: no hop block");
		this.backend.addHopListener(listener);
		waitFor(() -> this.api.hopsEnabled, 2000);
		for( int i = 1; i <= 3; i++ ) {
			float[] hop = new float[256];
			hop[0] = i;
			this.api.pushHop(hop);
		}
		waitFor(() -> heard.size() == 4, 2000);
		assertEquals("configure 48000", heard.get(0));
		assertEquals("hop 1.0 256", heard.get(1));
		assertEquals("hop 3.0 256", heard.get(3));
		this.backend.removeHopListener(listener);
		assertFalse(this.api.hopsEnabled);
	}

	@Test
	public void closeIsIdempotentAndStopsTheThreadsWithin500Milliseconds() throws Exception {
		List<AnalysisFrame> frames = new CopyOnWriteArrayList<AnalysisFrame>();
		this.backend.subscribe(frames::add);
		this.backend.acquire("test");
		assertTrue(this.api.isOpen());
		long start = System.nanoTime();
		this.backend.release("test");
		assertTrue(System.nanoTime() - start < 500_000_000L, "closed in " + (System.nanoTime() - start) / 1_000_000 + " ms");
		assertFalse(this.backend.isRunning(), "the frame and hop threads ended");
		this.backend.close();
		this.backend.release("test");
		assertEquals(1, this.api.closes.get());
		assertEquals(0, this.backend.getConsumerCount());
	}

	@Test
	public void whatWasAskedBeforeTheOpenReachesTheLibraryAtTheOpen() throws Exception {
		this.backend.setSpectrumEnabled(true);
		this.backend.setExpectedPitches(new int[] { 45 });
		this.backend.setExpectedChord(new int[] { 40, 47, 52 }, new int[] { 45 });
		this.backend.setAttackCandidates(new int[] { 40, 45, 47 });
		this.backend.acquire("test");
		assertTrue(this.api.spectrum);
		assertArrayEquals(new int[] { 45 }, this.api.expected);
		assertArrayEquals(new int[] { 40, 47, 52 }, this.api.chordRequired);
		assertArrayEquals(new int[] { 45 }, this.api.chordTolerated);
		assertArrayEquals(new int[] { 40, 45, 47 }, this.api.attack);
		assertArrayEquals(NativeParameters.of(RecognitionSettings.defaults()), this.api.openedParameters);
	}

	@Test
	public void theChosenDeviceAndChannelReachTheOpenUnchangedAndTheDefaultsAreEmptyAndMinusOne() throws Exception {
		this.selection.select(new AudioInputDescriptor("usb", "Scarlett 2i2 USB", 1, 2, "Scarlett 2i2 USB (2)", AudioInputDescriptor.Kind.INSTRUMENT, false), null);
		this.backend.acquire("test");
		assertEquals("Scarlett 2i2 USB", this.api.openedName);
		assertEquals(1, this.api.openedChannel);
		this.backend.release("test");
		this.selection.select(new AudioInputDescriptor("default", "Default Audio Device", null, 1, "System default", AudioInputDescriptor.Kind.UNKNOWN, true), null);
		this.backend.acquire("test");
		assertEquals("", this.api.openedName);
		assertEquals(-1, this.api.openedChannel);
	}

	static void waitFor(BooleanSupplier condition, long millis) throws InterruptedException {
		long deadline = System.nanoTime() + millis * 1_000_000L;
		while( !condition.getAsBoolean() ) {
			if( System.nanoTime() > deadline ) {
				throw new AssertionError("not reached within " + millis + " ms");
			}
			Thread.sleep(2L);
		}
	}
}
