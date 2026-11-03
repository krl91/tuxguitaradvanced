package app.tuxguitar.audio.engine;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.analysis.HopListener;
import app.tuxguitar.audio.engine.nativeengine.FrameRecords;
import app.tuxguitar.audio.engine.nativeengine.NativeRecognitionLibrary;
import app.tuxguitar.audio.engine.nativeengine.ParityEngines;
import app.tuxguitar.audio.engine.settings.PitchEngineCatalog;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;

/**
 * {@link OfflineAnalysis#analyze} runs the engine the settings and the system property choose (spec 023 T026, contract §3): {@code java} gives today's
 * loop field by field, {@code native} the records of {@link NativeRecognitionLibrary#analyze} (or Java when the library does not load); a hop listener
 * gets the same hops either way; the signatures are unchanged.
 */
public class OfflineAnalysisEngineDispatchTest {

	private static final int RATE = 48000;
	private static final int[] CANDIDATES = { 40, 45, 47, 52 };

	private final String saved = System.getProperty(PitchEngineCatalog.ENGINE_PROPERTY);

	@AfterEach
	public void restore() {
		if (this.saved == null) {
			System.clearProperty(PitchEngineCatalog.ENGINE_PROPERTY);
		} else {
			System.setProperty(PitchEngineCatalog.ENGINE_PROPERTY, this.saved);
		}
	}

	@Test
	public void javaGivesTodaysLoop() {
		System.setProperty(PitchEngineCatalog.ENGINE_PROPERTY, "java");
		float[] mono = signal();
		assertSameFrames(ParityEngines.java(mono, RATE, request()), OfflineAnalysis.analyze(mono, RATE, RecognitionSettings.defaults(), true, CANDIDATES));
	}

	@Test
	public void nativeGivesTheNativeRecordsOrJavaWithoutTheLibrary() {
		System.setProperty(PitchEngineCatalog.ENGINE_PROPERTY, "native");
		float[] mono = signal();
		List<AnalysisFrame> frames = OfflineAnalysis.analyze(mono, RATE, RecognitionSettings.defaults(), true, CANDIDATES);
		if (NativeRecognitionLibrary.load() == null) {
			assertSameFrames(ParityEngines.nativeFrames(mono, RATE, request()), frames);
		} else {
			assertSameFrames(ParityEngines.java(mono, RATE, request()), frames);
		}
	}

	@Test
	public void aHopListenerGetsTheSameHopsWithEitherEngine() {
		float[] mono = signal();
		for (String engine : new String[] { "java", "native" }) {
			System.setProperty(PitchEngineCatalog.ENGINE_PROPERTY, engine);
			List<float[]> hops = new ArrayList<float[]>();
			int[] configured = { 0 };
			OfflineAnalysis.analyze(mono, RATE, RecognitionSettings.defaults(), false, null, new HopListener() {
				public void configure(int sampleRate) {
					configured[0] = sampleRate;
				}

				public void hop(float[] samples, int offset, int length) {
					float[] copy = new float[length];
					System.arraycopy(samples, offset, copy, 0, length);
					hops.add(copy);
				}
			});
			Assertions.assertEquals(RATE, configured[0], engine);
			Assertions.assertEquals(mono.length / 256, hops.size(), engine);
			for (int h = 0; h < hops.size(); h++) {
				for (int i = 0; i < 256; i++) {
					Assertions.assertEquals(mono[h * 256 + i], hops.get(h)[i], engine + " hop " + h);
				}
			}
		}
	}

	private static ParityEngines.Request request() {
		ParityEngines.Request request = new ParityEngines.Request();
		request.spectrum = true;
		request.attackCandidates = CANDIDATES;
		return request;
	}

	/** Frames compared through their records: every field the record carries, bit for bit. */
	private static void assertSameFrames(List<AnalysisFrame> expected, List<AnalysisFrame> actual) {
		Assertions.assertEquals(expected.size(), actual.size());
		float[] a = new float[FrameRecords.CAPACITY];
		float[] b = new float[FrameRecords.CAPACITY];
		for (int i = 0; i < expected.size(); i++) {
			int la = FrameRecords.write(expected.get(i), 0L, a, 0);
			int lb = FrameRecords.write(actual.get(i), 0L, b, 0);
			Assertions.assertEquals(la, lb, "frame " + i);
			for (int k = 0; k < la; k++) {
				Assertions.assertEquals(Float.floatToRawIntBits(a[k]), Float.floatToRawIntBits(b[k]), "frame " + i + " slot " + k);
			}
			Assertions.assertNull(actual.get(i).getTechniqueFeatures());
		}
	}

	private static float[] signal() {
		float[] mono = new float[RATE * 2];
		for (int i = 0; i < mono.length; i++) {
			double t = i / (double) RATE;
			double note = t < 1.0 ? 110.0 : 146.83;
			double since = t < 1.0 ? t : t - 1.0;
			mono[i] = (float) (0.3 * Math.exp(-3 * since) * Math.sin(2 * Math.PI * note * t));
		}
		return mono;
	}
}
