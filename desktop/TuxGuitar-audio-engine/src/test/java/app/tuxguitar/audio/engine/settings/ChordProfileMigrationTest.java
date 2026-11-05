package app.tuxguitar.audio.engine.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * The table-driven migration of the chord settings (spec 024, research R7, task T014): a file whose chord values equal a past set of a level's
 * values reads as that level's current values; any other file is read as it is; the layout version does not change.
 */
public class ChordProfileMigrationTest {

	/** A file saved by spec 023: every key of that version, with its chord defaults, and no profile key. */
	private static Map<String, String> spec023File() {
		Map<String, String> file = new LinkedHashMap<String, String>(RecognitionSettings.defaults().toStrings());
		file.remove(RecognitionParameters.CHORD_PROFILE_MATCH.getKey());
		file.remove(RecognitionParameters.CHORD_PROFILE_NAMES.getKey());
		file.remove(RecognitionParameters.CHORD_ATTACK.getKey());
		file.remove(RecognitionParameters.CHORD_PROFILE_TOP.getKey());
		file.remove(RecognitionParameters.CHORD_PROFILE_SETTLE.getKey());
		file.put(RecognitionParameters.CHORD_COMPLETENESS.getKey(), RecognitionParameters.COMPLETENESS_ALL);
		file.put(RecognitionParameters.CHORD_EVIDENCE.getKey(), Double.toString(1.0 / 3.0));
		file.put(RecognitionParameters.CHORD_CONFIRM.getKey(), "43");
		return file;
	}

	@Test
	public void aSpec023FileReadsAsNormal() {
		RecognitionSettings read = RecognitionSettings.fromStrings(spec023File()).getSettings();
		assertEquals(RecognitionSettings.defaults(), read);
		assertEquals(RecognitionParameters.COMPLETENESS_PROFILE, read.get(RecognitionParameters.CHORD_COMPLETENESS));
	}

	@Test
	public void aSpec023FileWithAChordValueChangedByHandIsReadAsItIs() {
		Map<String, String> evidence = spec023File();
		evidence.put(RecognitionParameters.CHORD_EVIDENCE.getKey(), "0.5");
		RecognitionSettings read = RecognitionSettings.fromStrings(evidence).getSettings();
		assertEquals(RecognitionParameters.COMPLETENESS_ALL, read.get(RecognitionParameters.CHORD_COMPLETENESS));
		assertEquals(0.5, read.get(RecognitionParameters.CHORD_EVIDENCE), 1e-12);

		Map<String, String> confirm = spec023File();
		confirm.put(RecognitionParameters.CHORD_CONFIRM.getKey(), "60");
		RecognitionSettings readConfirm = RecognitionSettings.fromStrings(confirm).getSettings();
		assertEquals(RecognitionParameters.COMPLETENESS_ALL, readConfirm.get(RecognitionParameters.CHORD_COMPLETENESS));
		assertEquals(60.0, readConfirm.get(RecognitionParameters.CHORD_CONFIRM), 1e-12);
	}

	@Test
	public void aFileWithAProfileKeyIsReadAsItIs() {
		Map<String, String> file = spec023File();
		file.put(RecognitionParameters.CHORD_PROFILE_MATCH.getKey(), "0.85");
		RecognitionSettings read = RecognitionSettings.fromStrings(file).getSettings();
		assertEquals(RecognitionParameters.COMPLETENESS_ALL, read.get(RecognitionParameters.CHORD_COMPLETENESS));
		assertEquals(0.85, read.get(RecognitionParameters.CHORD_PROFILE_MATCH), 1e-12);
	}

	@Test
	public void theHistoryIsTableDriven() {
		Map<String, String> oldStrict = new HashMap<String, String>();
		oldStrict.put(RecognitionParameters.CHORD_COMPLETENESS.getKey(), RecognitionParameters.COMPLETENESS_PROFILE);
		oldStrict.put(RecognitionParameters.CHORD_PROFILE_MATCH.getKey(), "0.85");
		oldStrict.put(RecognitionParameters.CHORD_ATTACK.getKey(), "false");
		Map<String, String> oldLenient = new HashMap<String, String>();
		oldLenient.put(RecognitionParameters.CHORD_COMPLETENESS.getKey(), RecognitionParameters.COMPLETENESS_PROFILE);
		oldLenient.put(RecognitionParameters.CHORD_PROFILE_MATCH.getKey(), "0.60");
		oldLenient.put(RecognitionParameters.CHORD_ATTACK.getKey(), "true");
		List<ChordLeniency.PastSet> history = Arrays.asList(new ChordLeniency.PastSet(ChordLeniency.Level.STRICT, oldStrict),
				new ChordLeniency.PastSet(ChordLeniency.Level.LENIENT, oldLenient));
		// the levels' current values, as a later version would give them
		ChordLeniency.Applier current = (level, settings) -> settings.with(RecognitionParameters.CHORD_PROFILE_MATCH, level == ChordLeniency.Level.STRICT ? 0.90 : 0.65);

		Map<String, String> strictFile = new HashMap<String, String>(RecognitionSettings.defaults().toStrings());
		strictFile.putAll(oldStrict);
		RecognitionSettings strict = RecognitionSettings.fromStrings(strictFile, history, current).getSettings();
		assertEquals(0.90, strict.get(RecognitionParameters.CHORD_PROFILE_MATCH), 1e-12);

		Map<String, String> lenientFile = new HashMap<String, String>(RecognitionSettings.defaults().toStrings());
		lenientFile.putAll(oldLenient);
		assertEquals(0.65, RecognitionSettings.fromStrings(lenientFile, history, current).getSettings().get(RecognitionParameters.CHORD_PROFILE_MATCH), 1e-12);

		// one value off a past set: read as it is
		Map<String, String> oneOff = new HashMap<String, String>(strictFile);
		oneOff.put(RecognitionParameters.CHORD_ATTACK.getKey(), "true");
		RecognitionSettings asItIs = RecognitionSettings.fromStrings(oneOff, history, current).getSettings();
		assertEquals(0.85, asItIs.get(RecognitionParameters.CHORD_PROFILE_MATCH), 1e-12);
		assertEquals(Boolean.TRUE, asItIs.get(RecognitionParameters.CHORD_ATTACK));
	}

	@Test
	public void theLayoutVersionIsStillOne() {
		assertEquals(1, RecognitionParameters.VERSION);
		assertEquals("1", RecognitionSettings.defaults().toStrings().get(RecognitionParameters.VERSION_KEY));
	}
}
