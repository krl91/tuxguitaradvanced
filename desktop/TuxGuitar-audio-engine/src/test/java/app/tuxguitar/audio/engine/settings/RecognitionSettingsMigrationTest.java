package app.tuxguitar.audio.engine.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * An older configuration file is read as it was written (spec 026, T035, SC-005, FR-007): every stored value is kept, and every key spec 026
 * added, absent from the file, takes its default. The two files hold the keys the store wrote at v0.8.0-beta.7 (commit 23aecd6a7, 29 keys) and
 * at the end of spec 024 (commit 825235a0c, 34 keys), each with a value other than its default. The chord values are not a past set of
 * {@link ChordLeniency#HISTORY}, so the spec 024 migration leaves them as they are.
 */
public class RecognitionSettingsMigrationTest {

	@Test
	public void aFileOfVersionBeta7KeepsItsValuesAndReadsTheNewKeysAtTheirDefaults() {
		checkRead(beta7());
	}

	@Test
	public void aFileOfTheEndOfSpec024KeepsItsValuesAndReadsTheNewKeysAtTheirDefaults() {
		Map<String, String> file = beta7();
		file.put("recognition.chord.completeness", "profile");
		file.put("recognition.chord.profile.match", "0.7");
		file.put("recognition.chord.profile.names", "0.3");
		file.put("recognition.chord.attack", "false");
		file.put("recognition.chord.detail.top", "96");
		file.put("recognition.chord.detail.settle", "150");
		assertEquals(35, file.size(), "34 settings and the version");
		checkRead(file);
	}

	private static void checkRead(Map<String, String> file) {
		RecognitionSettings.Loaded loaded = RecognitionSettings.fromStrings(file);
		assertEquals(new LinkedHashMap<String, RecognitionParameter.Repair>(), loaded.getRepairs(), "nothing repaired");
		assertEquals(new ArrayList<String>(), loaded.getUnknownKeys(), "no unknown key");
		RecognitionSettings settings = loaded.getSettings();
		List<String> differences = new ArrayList<String>();
		int added = 0;
		for( RecognitionParameter parameter : RecognitionParameters.ALL ) {
			String raw = file.get(parameter.getKey());
			Object expected = raw != null ? parameter.parse(raw).getValue() : parameter.getDefault();
			if( raw == null ) {
				added++;
			}
			if( !expected.equals(settings.getValue(parameter)) ) {
				differences.add(parameter.getKey() + ": " + settings.getValue(parameter) + ", expected " + expected + (raw != null ? " (stored)" : " (default)"));
			}
		}
		assertEquals(new ArrayList<String>(), differences, "stored values kept, new keys at their defaults");
		assertEquals(RecognitionParameters.ALL.size() - (file.size() - 1), added, "every key the file lacks is read");
	}

	/** The 29 keys of v0.8.0-beta.7 (and the version), as its store wrote them, none at its default. */
	private static Map<String, String> beta7() {
		Map<String, String> file = new LinkedHashMap<String, String>();
		file.put("recognition.version", "1");
		file.put("recognition.input.level.min", "-45");
		file.put("recognition.input.level.max", "-10");
		file.put("recognition.input.noise-reduction", "true");
		file.put("recognition.input.gain-suggestion", "false");
		file.put("recognition.tuner.in-tune", "8");
		file.put("recognition.tuner.hold", "false");
		file.put("recognition.note.accept", "40");
		file.put("recognition.note.fret-sharp", "1.2");
		file.put("recognition.note.confidence", "0.7");
		file.put("recognition.note.stability", "150");
		file.put("recognition.note.attack", "6.0");
		file.put("recognition.note.legato", "strict");
		file.put("recognition.note.technique", "false");
		file.put("recognition.tempo.window", "150");
		file.put("recognition.chord.evidence", "0.5");
		file.put("recognition.chord.window", "900");
		file.put("recognition.chord.completeness", "all-verifiable");
		file.put("recognition.chord.foreign", "ignore");
		file.put("recognition.passage.measures", "4");
		file.put("recognition.passage.threshold", "75");
		file.put("recognition.passage.retries", "4");
		file.put("recognition.follow.play-time", "800");
		file.put("recognition.engine.pitch", "native");
		file.put("recognition.advanced.level.clip-sample", "0.95");
		file.put("recognition.advanced.level.clip-share", "0.2");
		file.put("recognition.advanced.tuner.confidence", "0.9");
		file.put("recognition.advanced.attack.min-level", "-60");
		file.put("recognition.advanced.chord.min-partials", "3");
		file.put("recognition.advanced.chord.confirm", "60");
		assertEquals(30, file.size(), "29 settings and the version");
		return file;
	}
}
