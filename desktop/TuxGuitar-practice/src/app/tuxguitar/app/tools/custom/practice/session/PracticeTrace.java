package app.tuxguitar.app.tools.custom.practice.session;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.analysis.AttackEvidence;

/**
 * Optional diagnostic trace of what Practice hears and decides, one line per analysis frame, for
 * understanding why a note was or was not recognised without recording any audio: only derived
 * numbers are written (pitch estimate, level, the evidence for the expected notes). Off unless the
 * system property {@code tuxguitar.practice.trace} names a file; the file is appended to.
 */
final class PracticeTrace {

	private static final String PATH = System.getProperty("tuxguitar.practice.trace");
	private static PrintWriter writer;

	private PracticeTrace() {
	}

	static boolean enabled() {
		return PATH != null && !PATH.isEmpty();
	}

	private static synchronized PrintWriter open() {
		if( writer == null ) {
			try {
				boolean fresh = !Files.exists(Paths.get(PATH));
				writer = new PrintWriter(Files.newBufferedWriter(Paths.get(PATH), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND), false);
				if( fresh ) {
					writer.println("time_s,event,expected,f0_hz,confidence,level,onset,total,maximum,strength,expected_amplitudes,neighbour_amplitudes,note");
				}
			} catch (IOException exception) {
				return null;
			}
		}
		return writer;
	}

	static synchronized void accepted(long timestampNanos, int eventIndex) {
		PrintWriter out = open();
		if( out != null ) {
			out.println(String.format(Locale.ROOT, "%.3f,%d,,,,,,,,,,,ACCEPTED", timestampNanos / 1e9, eventIndex));
			out.flush();
		}
	}

	static synchronized void frame(AnalysisFrame frame, int eventIndex, int[] expected, String note) {
		PrintWriter out = open();
		if( out == null ) {
			return;
		}
		AttackEvidence evidence = frame.getAttackEvidence();
		StringBuilder amplitudes = new StringBuilder();
		StringBuilder neighbours = new StringBuilder();
		if( evidence != null && expected != null ) {
			for( int pitch : expected ) {
				amplitudes.append(String.format(Locale.ROOT, "%d:%.4f ", pitch, evidence.amplitudeOf(pitch)));
				for( int delta : new int[] { -2, -1, 1, 2 } ) {
					neighbours.append(String.format(Locale.ROOT, "%d:%.4f ", pitch + delta, evidence.amplitudeOf(pitch + delta)));
				}
			}
		}
		out.println(String.format(Locale.ROOT, "%.3f,%d,%s,%.1f,%.2f,%s,%b,%.4f,%.4f,%.2f,%s,%s,%s", frame.getTimestampNanos() / 1e9, eventIndex,
				expected == null ? "" : java.util.Arrays.toString(expected).replace(", ", " "), frame.getF0Hz(), frame.getConfidence(), frame.getLevelState(), frame.isOnset(),
				evidence == null ? 0.0 : evidence.getTotal(), evidence == null ? 0.0 : evidence.getMaximum(), evidence == null ? 0.0 : evidence.getAttackStrength(),
				amplitudes.toString().trim(), neighbours.toString().trim(), note));
		out.flush();
	}
}
