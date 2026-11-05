package app.tuxguitar.app.tools.custom.practice.dataset;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.replay.RecordedAudio;
import app.tuxguitar.app.tools.custom.practice.session.ChordProfile;
import app.tuxguitar.audio.engine.OfflineAnalysis;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.settings.PitchEngineCatalog;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;

/**
 * Diagnostic of the IDMT-SMT-Audio-Effects triads under the chord rule by profile (spec 024, stop at T027): for the major and minor triads of the
 * NoFX folder, every root from G2 to A3, the summed note names of the strum (from its first onset, 200 ms and 1 s), the match, the weak names and
 * whether the Wait session accepts the take. A report, no assertion. Run with {@code -Dgroups=dataset -DexcludedGroups=}; the report goes to
 * {@code target/dataset-benchmark/idmt-triad-profile-<engine>.txt}.
 */
@Tag("dataset")
public class IdmtTriadProfileDiagnosticTest {

	private static final Path NOFX = Paths.get(System.getProperty("user.home"),
			"tuxguitar-datasets/effects/IDMT-SMT-AUDIO-EFFECTS/IDMT-SMT-AUDIO-EFFECTS/poly/Gitarre polyphon/Samples/NoFX");
	private static final String[] NAMES = { "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B" };

	@Test
	public void report() throws Exception {
		assumeTrue(Files.isDirectory(NOFX), "IDMT-SMT-Audio-Effects is not here");
		RecognitionSettings settings = RecognitionSettings.defaults();
		String engine = PitchEngineCatalog.resolveKind(settings).name().toLowerCase();
		StringBuilder report = new StringBuilder("IDMT-SMT-Audio-Effects NoFX triads, chord rule by profile, engine " + engine + "\n\n");
		for( int type : new int[] { 21, 22 } ) {
			int[] steps = (type == 21 ? new int[] { 0, 4, 7 } : new int[] { 0, 3, 7 });
			for( int root = 43 ; root <= 57 ; root ++ ) {
				final String prefix = "P65-" + root + type + "0-";
				Path file;
				try( java.util.stream.Stream<Path> stream = Files.list(NOFX) ) {
					file = stream.filter(p -> p.getFileName().toString().startsWith(prefix)).findFirst().orElse(null);
				}
				if( file == null ) {
					continue;
				}
				int[] pitches = { root + steps[0], root + steps[1], root + steps[2] };
				RecordedAudio audio = RecordedAudio.load(file);
				float[] samples = new float[audio.samples.length + audio.sampleRate / 2];
				System.arraycopy(audio.samples, 0, samples, audio.sampleRate / 2, audio.samples.length);
				List<AnalysisFrame> frames = OfflineAnalysis.analyze(samples, audio.sampleRate, settings, true, new int[0]);
				long onset = Long.MIN_VALUE;
				for( AnalysisFrame frame : frames ) {
					if( frame.isOnset() && frame.getSpectrum() != null ) {
						onset = frame.getTimestampNanos();
						break;
					}
				}
				report.append(String.format("%s %-8s %-12s accepted %-5s", file.getFileName(), type == 21 ? "major" : "minor", names(pitches),
						DatasetJudge.accepts(audio, pitches)));
				if( onset == Long.MIN_VALUE ) {
					report.append("  no onset\n");
					continue;
				}
				for( long span : new long[] { 200_000_000L, 1_000_000_000L } ) {
					ChordProfile profile = new ChordProfile();
					profile.reset(pitches, new int[0], 84, onset);
					for( AnalysisFrame frame : frames ) {
						long t = frame.getTimestampNanos();
						if( t >= onset && t <= onset + span && frame.getSpectrum() != null ) {
							profile.add(frame.getSpectrum());
						}
					}
					report.append(String.format("  | %4d ms match %.3f weak %d", span / 1_000_000L, profile.match(), profile.weakNames(0.40)));
				}
				report.append('\n').append("      1 s names:").append(names(frames, pitches, onset)).append('\n');
			}
			report.append('\n');
		}
		Path out = Paths.get("target", "dataset-benchmark", "idmt-triad-profile-" + engine + ".txt");
		Files.createDirectories(out.getParent());
		Files.write(out, report.toString().getBytes(StandardCharsets.UTF_8));
		System.out.println(report);
	}

	private static String names(int[] pitches) {
		StringBuilder text = new StringBuilder();
		for( int pitch : pitches ) {
			text.append(text.length() > 0 ? " " : "").append(NAMES[pitch % 12]).append(pitch / 12 - 1);
		}
		return text.toString();
	}

	/** The summed linear magnitude of each name over 1 s, from a semitone under the lowest pitch to 84, relative to the strongest name. */
	private static String names(List<AnalysisFrame> frames, int[] pitches, long onset) {
		double[] sums = new double[12];
		for( AnalysisFrame frame : frames ) {
			long t = frame.getTimestampNanos();
			if( t < onset || t > onset + 1_000_000_000L || frame.getSpectrum() == null ) {
				continue;
			}
			int min = frame.getSpectrum().getMinBandMidi();
			for( int midi = Math.max(min, pitches[0] - 1) ; midi <= Math.min(min + frame.getSpectrum().getBandCount() - 1, 84) ; midi ++ ) {
				double db = frame.getSpectrum().getBandDb(midi);
				if( db != Double.NEGATIVE_INFINITY && !Double.isNaN(db) ) {
					sums[midi % 12] += Math.pow(10.0, db / 20.0);
				}
			}
		}
		double strongest = 0.0;
		for( double sum : sums ) {
			strongest = Math.max(strongest, sum);
		}
		StringBuilder text = new StringBuilder();
		for( int k = 0 ; k < 12 ; k ++ ) {
			boolean required = false;
			for( int pitch : pitches ) {
				required |= pitch % 12 == k;
			}
			text.append(String.format(" %s%s%.2f", required ? "*" : "", NAMES[k], strongest > 0 ? sums[k] / strongest : 0.0));
		}
		return text.toString();
	}
}
