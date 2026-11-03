package app.tuxguitar.audio.engine.nativeengine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.settings.RecognitionSettings;

/**
 * The per-hop native analysis (amendment of 2026-11-03, during T039) gives the records of the offline analysis bit for bit, for the same requests: so a
 * Java capture analysed natively gives the frames of {@code OfflineAnalysis} with the native engine. Switching the spectrum on starts it afresh, setting
 * the attack candidates restarts the attack analysis, and a reset forgets the window.
 */
public class NativeHopAnalysisTest {

	private static final int RATE = 48000;
	private static final int HOP = 256;
	private static final int[] CANDIDATES = { 40, 45, 47, 52 };

	@BeforeAll
	public static void library() {
		ParityRecordings.requireNativeLibrary();
	}

	@Test
	public void hopByHopGivesTheOfflineRecords() throws Exception {
		float[] parameters = NativeParameters.of(RecognitionSettings.defaults());
		for (ParityRecordings.Recording recording : ParityRecordings.first(4)) {
			float[] offline = NativeRecognitionLibrary.analyze(recording.mono, recording.rate, parameters, true, CANDIDATES, new int[] { 45, 52 }, null, null);
			long handle = NativeRecognitionLibrary.openAnalysis(recording.rate, parameters);
			try {
				NativeRecognitionLibrary.setAnalysisSpectrum(handle, true);
				NativeRecognitionLibrary.setAnalysisAttackCandidates(handle, CANDIDATES);
				NativeRecognitionLibrary.setAnalysisExpectedPitches(handle, new int[] { 45, 52 });
				assertSameRecords(recording.toString(), offline, hops(handle, recording.mono, 0, recording.mono.length));
			} finally {
				NativeRecognitionLibrary.closeAnalysis(handle);
			}
		}
	}

	@Test
	public void theSpectrumStartsAfreshWhenSwitchedOn() throws Exception {
		float[] mono = signal();
		float[] parameters = NativeParameters.of(RecognitionSettings.defaults());
		int half = (mono.length / 2) / HOP * HOP;
		long handle = NativeRecognitionLibrary.openAnalysis(RATE, parameters);
		try {
			hops(handle, mono, 0, half);
			NativeRecognitionLibrary.setAnalysisSpectrum(handle, true);
			NativeRecognitionLibrary.resetAnalysis(handle);
			float[] second = Arrays.copyOfRange(mono, half, mono.length);
			assertSameRecords("second half", NativeRecognitionLibrary.analyze(second, RATE, parameters, true, null, null, null, null),
					hops(handle, mono, half, mono.length));
		} finally {
			NativeRecognitionLibrary.closeAnalysis(handle);
		}
	}

	@Test
	public void newAttackCandidatesRestartTheAttackAnalysis() throws Exception {
		float[] mono = signal();
		float[] parameters = NativeParameters.of(RecognitionSettings.defaults());
		int half = (mono.length / 2) / HOP * HOP;
		long handle = NativeRecognitionLibrary.openAnalysis(RATE, parameters);
		try {
			NativeRecognitionLibrary.setAnalysisAttackCandidates(handle, new int[] { 40 });
			hops(handle, mono, 0, half);
			NativeRecognitionLibrary.resetAnalysis(handle);
			NativeRecognitionLibrary.setAnalysisAttackCandidates(handle, CANDIDATES);
			float[] second = Arrays.copyOfRange(mono, half, mono.length);
			assertSameRecords("second half", NativeRecognitionLibrary.analyze(second, RATE, parameters, false, CANDIDATES, null, null, null),
					hops(handle, mono, half, mono.length));
		} finally {
			NativeRecognitionLibrary.closeAnalysis(handle);
		}
	}

	@Test
	public void theFirstFramesWaitForAFullWindow() throws Exception {
		long handle = NativeRecognitionLibrary.openAnalysis(RATE, NativeParameters.of(RecognitionSettings.defaults()));
		try {
			float[] record = new float[FrameRecords.CAPACITY];
			float[] hop = new float[HOP];
			for (int i = 0; i < 7; i++) {
				Assertions.assertEquals(0, NativeRecognitionLibrary.analyzeHop(handle, hop, record), "hop " + i);
			}
			Assertions.assertTrue(NativeRecognitionLibrary.analyzeHop(handle, hop, record) > 0, "the eighth hop fills the window");
			Assertions.assertEquals(-1, NativeRecognitionLibrary.analyzeHop(handle, hop, new float[4]), "a record that does not fit is refused");
		} finally {
			NativeRecognitionLibrary.closeAnalysis(handle);
		}
	}

	/** The records of the hops of {@code mono} from {@code from} to {@code to}, one after another, as {@code analyze} returns them. */
	private static float[] hops(long handle, float[] mono, int from, int to) {
		List<float[]> records = new ArrayList<float[]>();
		float[] record = new float[FrameRecords.CAPACITY];
		float[] hop = new float[HOP];
		int total = 0;
		for (int offset = from; offset + HOP <= to; offset += HOP) {
			System.arraycopy(mono, offset, hop, 0, HOP);
			int length = NativeRecognitionLibrary.analyzeHop(handle, hop, record);
			Assertions.assertTrue(length >= 0, "the record fits");
			if (length > 0) {
				records.add(Arrays.copyOf(record, length));
				total += length;
			}
		}
		float[] all = new float[total];
		int at = 0;
		for (float[] r : records) {
			System.arraycopy(r, 0, all, at, r.length);
			at += r.length;
		}
		return all;
	}

	/** The same records, bit for bit, except the timestamps (the per-hop analysis leaves them to Java). */
	private static void assertSameRecords(String label, float[] expected, float[] actual) {
		Assertions.assertEquals(expected.length, actual.length, label + " length");
		int offset = 0;
		int frame = 0;
		while (offset < expected.length) {
			int length = FrameRecords.length(expected, offset);
			Assertions.assertEquals(length, FrameRecords.length(actual, offset), label + " frame " + frame + " length");
			for (int k = 0; k < length; k++) {
				if (k == FrameRecords.TIMESTAMP_NANOS || k == FrameRecords.TIMESTAMP_NANOS + 1) {
					continue;
				}
				Assertions.assertEquals(Float.floatToRawIntBits(expected[offset + k]), Float.floatToRawIntBits(actual[offset + k]), label + " frame " + frame + " slot " + k);
			}
			offset += length;
			frame++;
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
