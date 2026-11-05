package app.tuxguitar.audio.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.analysis.PitchEstimate;
import app.tuxguitar.audio.engine.analysis.PitchEstimator;
import app.tuxguitar.audio.engine.backend.FakeRecognitionApi;
import app.tuxguitar.audio.engine.backend.NativeBackend;
import app.tuxguitar.audio.engine.backend.RecognitionBackendFactory;
import app.tuxguitar.audio.engine.input.AudioInputDescriptor;
import app.tuxguitar.audio.engine.input.CurrentInputSelection;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.audio.engine.settings.PitchEngineCatalog;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.audio.engine.settings.RecognitionSettingsStore;
import app.tuxguitar.thread.TGMultiThreadHandler;
import app.tuxguitar.thread.TGThreadManager;
import app.tuxguitar.util.TGContext;

/**
 * A level check asked for while one runs does not start a second one (spec 025, FR-003, research R2): the running check keeps what it heard and
 * concludes on time, and is told whether a check is running.
 */
public class LevelCheckNotTwiceTest {

	private static final IntFunction<PitchEstimator> ESTIMATORS = rate -> window -> new PitchEstimate(220.0, 0.9, true);
	private static final long FRAME_NANOS = 20_000_000L;

	private TGContext context;
	private FakeRecognitionApi api;
	private AudioEngine engine;

	@BeforeEach
	public void setUp() {
		this.context = new TGContext();
		TGThreadManager.getInstance(this.context).setThreadHandler(new TGMultiThreadHandler());
		this.api = new FakeRecognitionApi();
		RecognitionBackendFactory factory = new RecognitionBackendFactory() {
			@Override
			public PitchEngineCatalog.Kind resolveKind(RecognitionSettings settings) {
				return PitchEngineCatalog.Kind.NATIVE;
			}

			@Override
			public NativeBackend createNative(TGContext context, CurrentInputSelection selection, Supplier<float[]> parameters) {
				return new NativeBackend(context, selection, LevelCheckNotTwiceTest.this.api, parameters);
			}
		};
		this.engine = BackendTestEngines.withBackends(this.context, new RecognitionSettingsStore(new Memory()), ESTIMATORS, factory);
		this.engine.getSelection().select(new AudioInputDescriptor("usb", "Rocksmith USB Guitar Adapter", 0, 1, "Rocksmith", AudioInputDescriptor.Kind.INSTRUMENT, false), null);
	}

	@AfterEach
	public void tearDown() {
		this.engine.release("a");
		TGThreadManager.getInstance(this.context).dispose();
	}

	@Test
	public void aSecondCheckAskedDuringOneKeepsTheRunningOneAndItConcludesOnTime() throws Exception {
		List<AnalysisFrame> frames = new CopyOnWriteArrayList<AnalysisFrame>();
		this.engine.subscribe(frames::add);
		this.engine.acquire("a");
		assertTrue(this.engine.isLevelCheckRunning(), "the acquire of an input never checked starts a check");
		// 300 ms of playing: the check needs 500 ms
		long sample = 0;
		for( long t = 0; t <= 300_000_000L; t += FRAME_NANOS ) {
			this.api.push(FakeRecognitionApi.frame(t, sample += 960), 1L);
		}
		waitFor(() -> frames.size() == 16, 2000);
		this.engine.runLevelCheck();
		assertTrue(this.engine.isLevelCheckRunning());
		// 260 ms more: 560 ms in all, enough for the running check, not for one started again at 320 ms
		for( long t = 320_000_000L; t <= 580_000_000L; t += FRAME_NANOS ) {
			this.api.push(FakeRecognitionApi.frame(t, sample += 960), 1L);
		}
		waitFor(() -> frames.size() == 30, 2000);
		waitFor(() -> this.engine.getSelection().snapshot().getAssessment().getStatus() != InputAssessment.Status.UNCHECKED, 2000);
		assertEquals(InputAssessment.Status.SUITABLE, this.engine.getSelection().snapshot().getAssessment().getStatus());
		assertFalse(this.engine.isLevelCheckRunning(), "concluded");
	}

	private static void waitFor(BooleanSupplier condition, long millis) throws InterruptedException {
		long deadline = System.nanoTime() + millis * 1_000_000L;
		while( !condition.getAsBoolean() ) {
			if( System.nanoTime() > deadline ) {
				throw new AssertionError("not reached within " + millis + " ms");
			}
			Thread.sleep(5L);
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
