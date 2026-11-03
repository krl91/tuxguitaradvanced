package app.tuxguitar.audio.engine.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.analysis.StrumClipMeter;
import app.tuxguitar.audio.engine.analysis.StrumVerdict;
import app.tuxguitar.audio.engine.input.CurrentInputSelection;
import app.tuxguitar.audio.engine.nativeengine.NativeParameters;
import app.tuxguitar.audio.engine.nativeengine.NativeRecognitionLibrary;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.thread.TGMultiThreadHandler;
import app.tuxguitar.thread.TGThreadManager;
import app.tuxguitar.util.TGContext;

/** On the native backend the pre-run check reads the native strum clip measure (spec 023, T045): its verdicts are those of {@link StrumClipMeter}. */
public class NativeStrumClipTest {

	private static final int RATE = 48000;

	@Test
	public void theReadingAndItsRestartGoThroughTheLibrary() throws Exception {
		TGContext context = new TGContext();
		TGThreadManager.getInstance(context).setThreadHandler(new TGMultiThreadHandler());
		FakeRecognitionApi api = new FakeRecognitionApi();
		NativeBackend backend = new NativeBackend(context, CurrentInputSelection.getInstance(context), api, () -> NativeParameters.of(RecognitionSettings.defaults()));
		try {
			backend.acquire("pre-run");
			api.strum = new float[] { StrumVerdict.TOO_WEAK.ordinal(), 0.0f, -47.5f };
			StrumClipMeter.Reading reading = backend.strumReading();
			assertEquals(StrumVerdict.TOO_WEAK, reading.verdict());
			assertEquals(-47.5, reading.rmsDbfs(), 1e-6);
			api.strum = new float[] { StrumVerdict.CLIPPING.ordinal(), 0.25f, -3.0f };
			assertEquals(StrumVerdict.CLIPPING, backend.strumReading().verdict());
			assertEquals(0.25, backend.strumReading().clippedShare(), 1e-6);
			backend.resetStrumReading();
			assertEquals(1, api.strumResets.get());
		} finally {
			backend.close();
			TGThreadManager.getInstance(context).dispose();
		}
	}

	@Test
	public void beforeTheOpenThereIsNoStrum() {
		TGContext context = new TGContext();
		NativeBackend backend = new NativeBackend(context, CurrentInputSelection.getInstance(context), new FakeRecognitionApi(), () -> NativeParameters.of(RecognitionSettings.defaults()));
		assertEquals(StrumVerdict.NO_STRUM, backend.strumReading().verdict());
		assertTrue(Double.isNaN(backend.strumReading().rmsDbfs()));
	}

	@Test
	public void theNativeMeasureGivesTheVerdictsOfTheJavaMeterOnTheSameSamples() {
		Assumptions.assumeTrue(NativeRecognitionLibrary.load() == null, "the native library is not built");
		Set<StrumVerdict> seen = EnumSet.noneOf(StrumVerdict.class);
		for( double amplitude : new double[] { 0.0, 0.004, 0.08, 0.6, 1.6 } ) {
			float[] mono = strum(amplitude);
			StrumClipMeter meter = new StrumClipMeter(StrumClipMeter.Parameters.of(RecognitionSettings.defaults()));
			meter.configure(RATE);
			for( int offset = 0; offset + 256 <= mono.length; offset += 256 ) {
				meter.hop(mono, offset, 256);
			}
			StrumClipMeter.Reading expected = meter.snapshot();
			StrumClipMeter.Reading actual = NativeBackend.readingOf(NativeRecognitionLibrary.strumClipOffline(mono, RATE, NativeParameters.of(RecognitionSettings.defaults())));
			assertEquals(expected.verdict(), actual.verdict(), "amplitude " + amplitude);
			seen.add(actual.verdict());
		}
		assertTrue(seen.size() >= 3, "several verdicts compared: " + seen);
	}

	/** Half a second of silence, then a decaying low E with its octave, cut at full scale. */
	private static float[] strum(double amplitude) {
		float[] mono = new float[RATE * 2];
		for( int i = RATE / 2; i < mono.length; i++ ) {
			double t = (i - RATE / 2) / (double) RATE;
			double value = amplitude * Math.exp(-t / 0.6) * (Math.sin(2 * Math.PI * 82.41 * t) + 0.5 * Math.sin(2 * Math.PI * 164.81 * t));
			mono[i] = (float) Math.max(-1.0, Math.min(1.0, value));
		}
		return mono;
	}
}
