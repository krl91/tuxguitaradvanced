package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.calibration.DeviceKey;
import app.tuxguitar.audio.engine.calibration.FrontEndState;
import app.tuxguitar.audio.engine.calibration.RecognitionProfile;
import app.tuxguitar.audio.engine.calibration.RecognitionRate;
import app.tuxguitar.audio.engine.settings.RecognitionParameter;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.audio.engine.settings.RecognitionSettingsStore;

/**
 * Spec 013 (T011): the writers of the recognition store write the DEFAULT layer. With a device profile active, saving the Configuration page must not copy
 * the profile's values into the default (FR-010); the sites were found by grep on 2026-10-15: {@code AudioRecognitionOption} (page, commit, restore) and
 * {@code FollowSettings.setWindowMillis} (tested in the session package).
 */
public class StoreWritersTest {

	private static final class Memory implements RecognitionSettingsStore.Persistence {
		Map<String, String> values = new HashMap<String, String>();

		public Map<String, String> read() {
			return new HashMap<String, String>(this.values);
		}

		public void write(Map<String, String> values) {
			this.values = new HashMap<String, String>(values);
		}
	}

	private static RecognitionProfile profile(double noteAccept) {
		Map<RecognitionParameter.Number, Double> values = new LinkedHashMap<RecognitionParameter.Number, Double>();
		values.put(RecognitionParameters.NOTE_ACCEPT, noteAccept);
		return new RecognitionProfile(DeviceKey.of("Micro MacBook Pro", null, 0), 48000, 0L, values, new RecognitionRate(9, 10, 2, 3),
				new RecognitionRate(7, 10, 1, 3), new FrontEndState(1.0, false), RecognitionProfile.Origin.SEARCHED);
	}

	private static double profileValue() {
		RecognitionParameter.Number p = RecognitionParameters.NOTE_ACCEPT;
		return p.getMinimum() == p.getDefault() ? p.getMaximum() : p.getMinimum();
	}

	@Test
	public void theConfigurationPageStartsFromTheDefaultLayerNotFromTheProfile() {
		Memory file = new Memory();
		RecognitionSettingsStore store = new RecognitionSettingsStore(file);
		store.setActiveProfile(profile(profileValue()));
		assertNotEquals(store.getDefault(), store.get(), "a profile is in force");
		assertEquals(store.getDefault(), AudioRecognitionOption.initialSettings(store), "the page shows the default configuration");
	}

	@Test
	public void savingThePageWithAProfileActiveLeavesTheProfileOutOfTheDefaultAndOfTheFile() {
		Memory file = new Memory();
		RecognitionSettingsStore store = new RecognitionSettingsStore(file);
		double defaultAccept = store.getDefault().get(RecognitionParameters.NOTE_ACCEPT);
		store.setActiveProfile(profile(profileValue()));
		RecognitionSettings pending = AudioRecognitionOption.initialSettings(store).with(RecognitionParameters.TEMPO_WINDOW, 130.0);
		AudioRecognitionOption.commit(store, pending);
		assertEquals(defaultAccept, store.getDefault().get(RecognitionParameters.NOTE_ACCEPT), "the profile's value is not in the default");
		assertEquals(RecognitionParameters.NOTE_ACCEPT.format(defaultAccept), file.values.get(RecognitionParameters.NOTE_ACCEPT.getKey()));
		assertEquals(130.0, store.getDefault().get(RecognitionParameters.TEMPO_WINDOW), "what the player changed is saved");
		assertEquals(profileValue(), store.get().get(RecognitionParameters.NOTE_ACCEPT), "and the profile is still in force");
	}

	@Test
	public void tuxGuitarsDefaultsButtonActsOnTheDefaultLayerAndKeepsTheProfile() {
		RecognitionSettingsStore store = new RecognitionSettingsStore(new Memory());
		store.update(store.getDefault().with(RecognitionParameters.TEMPO_WINDOW, 130.0));
		store.setActiveProfile(profile(profileValue()));
		AudioRecognitionOption.applyTuxGuitarDefaults(store);
		assertEquals(RecognitionSettings.defaults(), store.getDefault());
		assertEquals(profileValue(), store.get().get(RecognitionParameters.NOTE_ACCEPT));
	}

	private static List<Path> productionSources() throws IOException {
		Path here = Paths.get(System.getProperty("user.dir"));
		Path[] roots = { here.resolve("src"), here.resolve("../TuxGuitar-tuner-next/src").normalize(), here.resolve("../TuxGuitar-audio-engine/src/main").normalize() };
		java.util.ArrayList<Path> files = new java.util.ArrayList<Path>();
		for (Path root : roots) {
			assertTrue(Files.isDirectory(root), "the production sources are there: " + root);
			try (Stream<Path> walk = Files.walk(root)) {
				files.addAll(walk.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList()));
			}
		}
		return files;
	}

	@Test
	public void noProductionCodeWritesTheStoreFromTheEffectiveSettings() throws IOException {
		Pattern writeFromGet = Pattern.compile("\\.update\\([^;]*\\b\\w*[sS]tore\\w*\\.get\\(\\)");
		for (Path file : productionSources()) {
			String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
			assertFalse(writeFromGet.matcher(text).find(), file.getFileName() + " writes the store from get(): it must start from getDefault()");
		}
	}

	@Test
	public void theConfigurationPageIsBuiltFromTheDefaultLayer() throws IOException {
		String source = new String(Files.readAllBytes(Paths.get(System.getProperty("user.dir"),
				"src/app/tuxguitar/app/tools/custom/practice/ui/AudioRecognitionOption.java")), StandardCharsets.UTF_8);
		int page = source.indexOf("new RecognitionSettingsPanel(");
		assertTrue(page >= 0);
		String call = source.substring(page, source.indexOf("new RecognitionSettingsPanel.InputSection", page));
		assertFalse(call.contains("store.get()"), "the page is not built from the effective settings");
		assertTrue(call.contains("initialSettings("), "the page asks for its initial settings");
	}
}
