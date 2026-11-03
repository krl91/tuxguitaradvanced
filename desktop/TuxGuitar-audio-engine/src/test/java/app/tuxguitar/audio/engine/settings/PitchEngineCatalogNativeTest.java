package app.tuxguitar.audio.engine.settings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.settings.PitchEngineCatalog.Engine;
import app.tuxguitar.audio.engine.settings.PitchEngineCatalog.Kind;

/**
 * The engines of the menu (spec 023, T059, data-model "Engine"): the native engine first when its library loads, then Java; Java only when it does not;
 * Automatic stands for the first one. The Java pitch estimators ({@code getEngines()}, {@code getRecommended()}, {@code resolve()}) are unchanged, a stored
 * choice is kept and the stored key is the same.
 */
public class PitchEngineCatalogNativeTest {

	private static final String LOADED = null;
	private static final String MISSING = "no library here";

	@Test
	public void theJavaPitchEstimatorsAreUnchanged() {
		Assertions.assertEquals(1, PitchEngineCatalog.getEngines().size());
		Assertions.assertEquals(PitchEngineCatalog.FASTYIN, PitchEngineCatalog.getRecommended().getId());
		Assertions.assertEquals(PitchEngineCatalog.getRecommended(), PitchEngineCatalog.resolve(PitchEngineCatalog.AUTOMATIC));
		Assertions.assertEquals(PitchEngineCatalog.getRecommended(), PitchEngineCatalog.resolve(PitchEngineCatalog.NATIVE), "the estimators know no native engine");
		Assertions.assertEquals(Kind.JAVA, PitchEngineCatalog.getRecommended().kind());
	}

	@Test
	public void theNativeEngineComesFirstWhenItsLibraryLoads() {
		List<Engine> engines = PitchEngineCatalog.recognitionEngines(LOADED);
		Assertions.assertEquals(Arrays.asList(PitchEngineCatalog.NATIVE, PitchEngineCatalog.FASTYIN), ids(engines));
		Assertions.assertEquals(Arrays.asList("Native (Rust)", "Java (FastYin)"), names(engines));
		Assertions.assertEquals(Kind.NATIVE, engines.get(0).kind());
		Assertions.assertEquals(Kind.JAVA, engines.get(1).kind());
		Assertions.assertNotNull(engines.get(0).create(48000), "an estimator for the callers that only need one");
		Assertions.assertEquals(engines.get(0), PitchEngineCatalog.recommendedRecognitionEngine(LOADED));
	}

	@Test
	public void onlyJavaIsListedWhenTheLibraryDoesNotLoad() {
		List<Engine> engines = PitchEngineCatalog.recognitionEngines(MISSING);
		Assertions.assertEquals(Arrays.asList(PitchEngineCatalog.FASTYIN), ids(engines));
		Assertions.assertEquals(Arrays.asList("Java (FastYin)"), names(engines));
		Assertions.assertEquals(engines.get(0), PitchEngineCatalog.recommendedRecognitionEngine(MISSING));
	}

	@Test
	public void thePublicListFollowsTheLibrary() {
		String failure = app.tuxguitar.audio.engine.nativeengine.NativeRecognitionLibrary.load();
		Assertions.assertEquals(ids(PitchEngineCatalog.recognitionEngines(failure)), ids(PitchEngineCatalog.getRecognitionEngines()));
		Assertions.assertEquals(PitchEngineCatalog.getRecognitionEngines().get(0), PitchEngineCatalog.getRecommendedRecognitionEngine());
	}

	@Test
	public void automaticStandsForTheFirstEngineListed() {
		PitchEngineCatalog.FallbackLog log = new PitchEngineCatalog.FallbackLog(line -> {
		});
		Assertions.assertEquals(Kind.NATIVE, PitchEngineCatalog.resolveKind(PitchEngineCatalog.AUTOMATIC, null, LOADED, log));
		Assertions.assertEquals(Kind.JAVA, PitchEngineCatalog.resolveKind(PitchEngineCatalog.AUTOMATIC, null, MISSING, log));
		Assertions.assertEquals(Kind.NATIVE, PitchEngineCatalog.resolveKind("neural-net", null, LOADED, log), "an unknown value reads as Automatic");
	}

	@Test
	public void aStoredChoiceIsKeptAndAnUnknownOneReadsAsAutomatic() {
		RecognitionParameter.Choice choice = RecognitionParameters.ENGINE_PITCH;
		Assertions.assertEquals("recognition.engine.pitch", choice.getKey());
		Assertions.assertEquals(PitchEngineCatalog.FASTYIN, choice.parse("fastyin").getValue());
		Assertions.assertEquals(RecognitionParameter.Repair.NONE, choice.parse("fastyin").getRepair());
		Assertions.assertEquals(PitchEngineCatalog.NATIVE, choice.parse("native").getValue());
		Assertions.assertEquals(PitchEngineCatalog.AUTOMATIC, choice.parse("neural-net").getValue());
		Assertions.assertEquals(RecognitionParameter.Repair.DEFAULTED, choice.parse("neural-net").getRepair());
		RecognitionSettings java = RecognitionSettings.defaults().with(choice, PitchEngineCatalog.FASTYIN);
		Assertions.assertEquals(PitchEngineCatalog.FASTYIN, java.get(choice));
		Assertions.assertEquals(Kind.JAVA, PitchEngineCatalog.resolveKind(java));
	}

	@Test
	public void theNativeLibraryFailureIsToldOnlyWhenTheNativeEngineIsAsked() {
		Assertions.assertEquals(MISSING, PitchEngineCatalog.nativeLibraryFailure(PitchEngineCatalog.NATIVE, null, MISSING));
		Assertions.assertEquals(MISSING, PitchEngineCatalog.nativeLibraryFailure(PitchEngineCatalog.AUTOMATIC, "native", MISSING));
		Assertions.assertNull(PitchEngineCatalog.nativeLibraryFailure(PitchEngineCatalog.AUTOMATIC, null, MISSING), "Automatic is Java without the library");
		Assertions.assertNull(PitchEngineCatalog.nativeLibraryFailure(PitchEngineCatalog.FASTYIN, null, MISSING));
		Assertions.assertNull(PitchEngineCatalog.nativeLibraryFailure(PitchEngineCatalog.NATIVE, null, LOADED));
	}

	private static List<String> ids(List<Engine> engines) {
		List<String> ids = new ArrayList<String>();
		for( Engine engine : engines ) {
			ids.add(engine.getId());
		}
		return ids;
	}

	private static List<String> names(List<Engine> engines) {
		List<String> names = new ArrayList<String>();
		for( Engine engine : engines ) {
			names.add(engine.getName());
		}
		return names;
	}
}
