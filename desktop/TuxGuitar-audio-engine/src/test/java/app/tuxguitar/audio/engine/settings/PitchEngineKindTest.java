package app.tuxguitar.audio.engine.settings;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.settings.PitchEngineCatalog.Kind;

/**
 * Which engine a stored choice runs (spec 023, contract §3, data-model "Recognition engine"): {@code fastyin} is Java, {@code native} is native,
 * Automatic and any other value follow the recommended engine; the system property {@code tuxguitar.recognition.engine} overrides Automatic only, an
 * empty or blank property counting as unset; native asked while the library does not load gives Java and one log line per process.
 */
public class PitchEngineKindTest {

	private static final String LOADED = null;

	private final List<String> lines = new ArrayList<String>();
	private final PitchEngineCatalog.FallbackLog log = new PitchEngineCatalog.FallbackLog(this.lines::add);

	@Test
	public void eachStoredChoiceHasItsKind() {
		Assertions.assertEquals(Kind.JAVA, resolve(PitchEngineCatalog.FASTYIN, null));
		Assertions.assertEquals(Kind.NATIVE, resolve(PitchEngineCatalog.NATIVE, null));
		Assertions.assertEquals(PitchEngineCatalog.getRecommended().kind(), resolve(PitchEngineCatalog.AUTOMATIC, null));
		Assertions.assertEquals(PitchEngineCatalog.getRecommended().kind(), resolve("something-else", null));
		Assertions.assertEquals(PitchEngineCatalog.getRecommended().kind(), resolve(null, null));
		Assertions.assertTrue(this.lines.isEmpty());
	}

	@Test
	public void theEnginesKnowTheirKind() {
		Assertions.assertEquals(Kind.JAVA, PitchEngineCatalog.resolve(PitchEngineCatalog.FASTYIN).kind());
		Assertions.assertEquals(Kind.JAVA, PitchEngineCatalog.getRecommended().kind(), "Automatic still means Java in this phase");
	}

	@Test
	public void thePropertyOverridesAutomaticOnly() {
		Assertions.assertEquals(Kind.NATIVE, resolve(PitchEngineCatalog.AUTOMATIC, "native"));
		Assertions.assertEquals(Kind.JAVA, resolve(PitchEngineCatalog.AUTOMATIC, "java"));
		Assertions.assertEquals(Kind.JAVA, resolve(PitchEngineCatalog.FASTYIN, "native"), "a hand-made fastyin is kept");
		Assertions.assertEquals(Kind.NATIVE, resolve(PitchEngineCatalog.NATIVE, "java"), "a hand-made native is kept");
		Assertions.assertEquals(Kind.NATIVE, resolve(PitchEngineCatalog.AUTOMATIC, " NATIVE "));
	}

	@Test
	public void anEmptyOrBlankPropertyCountsAsUnset() {
		Kind recommended = PitchEngineCatalog.getRecommended().kind();
		Assertions.assertEquals(recommended, resolve(PitchEngineCatalog.AUTOMATIC, ""));
		Assertions.assertEquals(recommended, resolve(PitchEngineCatalog.AUTOMATIC, "   "));
		Assertions.assertEquals(recommended, resolve(PitchEngineCatalog.AUTOMATIC, "unknown"));
	}

	@Test
	public void nativeWithoutTheLibraryGivesJavaAndOneLogLine() {
		Assertions.assertEquals(Kind.JAVA, PitchEngineCatalog.resolveKind(PitchEngineCatalog.NATIVE, null, "no library here", this.log));
		Assertions.assertEquals(Kind.JAVA, PitchEngineCatalog.resolveKind(PitchEngineCatalog.AUTOMATIC, "native", "no library here", this.log));
		Assertions.assertEquals(Kind.JAVA, PitchEngineCatalog.resolveKind(PitchEngineCatalog.NATIVE, null, "no library here", this.log));
		Assertions.assertEquals(1, this.lines.size(), String.valueOf(this.lines));
		Assertions.assertTrue(this.lines.get(0).contains("no library here"), this.lines.get(0));
		Assertions.assertEquals(Kind.JAVA, PitchEngineCatalog.resolveKind(PitchEngineCatalog.FASTYIN, null, "no library here", this.log));
		Assertions.assertEquals(1, this.lines.size());
	}

	@Test
	public void theSettingsOverloadReadsTheStoredChoice() {
		String property = System.getProperty(PitchEngineCatalog.ENGINE_PROPERTY);
		String loadFailure = app.tuxguitar.audio.engine.nativeengine.NativeRecognitionLibrary.load();
		Kind expected = PitchEngineCatalog.resolveKind(PitchEngineCatalog.AUTOMATIC, property, loadFailure, new PitchEngineCatalog.FallbackLog(line -> {
		}));
		Assertions.assertEquals(expected, PitchEngineCatalog.resolveKind(RecognitionSettings.defaults()));
	}

	private Kind resolve(String choice, String property) {
		return PitchEngineCatalog.resolveKind(choice, property, LOADED, this.log);
	}
}
