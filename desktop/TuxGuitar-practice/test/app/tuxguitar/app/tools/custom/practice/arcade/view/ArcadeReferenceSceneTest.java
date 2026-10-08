package app.tuxguitar.app.tools.custom.practice.arcade.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneItem.Role;

/**
 * The structure of the fixed reference scenes against the reference files (spec 003 T043). The files are the <em>candidate</em> reference until the owner
 * has reviewed the images (T044); changing them is changing the look and must be done on purpose: write them again with
 * {@code -Dtuxguitar.arcade.reference.write=true}.
 */
public class ArcadeReferenceSceneTest {

	public static final Path DIRECTORY = Paths.get("test/app/tuxguitar/app/tools/custom/practice/arcade/reference");

	public static String expected(String name) throws IOException {
		return new String(Files.readAllBytes(DIRECTORY.resolve(name + ".scene.txt")), StandardCharsets.UTF_8);
	}

	private void check(ReferenceScenes.Reference reference) throws IOException {
		String actual = SceneCapture.of(reference.scene(false, QualityTier.FULL));
		if (Boolean.getBoolean("tuxguitar.arcade.reference.write")) {
			Files.createDirectories(DIRECTORY);
			Files.write(DIRECTORY.resolve(reference.name + ".scene.txt"), actual.getBytes(StandardCharsets.UTF_8));
		}
		assertEquals(expected(reference.name), actual, "the structure of the " + reference.name + " reference scene changed");
	}

	@Test
	public void theSixStringSceneIsTheReference() throws IOException {
		check(ReferenceScenes.sixStrings());
	}

	@Test
	public void theFourStringSceneIsTheReference() throws IOException {
		check(ReferenceScenes.fourStrings());
	}

	@Test
	public void theNamedChordSceneIsTheReference() throws IOException {
		check(ReferenceScenes.namedChord());
	}

	@Test
	public void theUnnamedChordSceneIsTheReference() throws IOException {
		check(ReferenceScenes.unnamedChord());
	}

	@Test
	public void theSingleNoteSceneIsTheReference() throws IOException {
		check(ReferenceScenes.singleNote());
	}

	@Test
	public void theDensePassageSceneIsTheReference() throws IOException {
		check(ReferenceScenes.denseRealistic());
	}

	@Test
	public void theDensePassageReferenceIsReadable() {
		Scene scene = ReferenceScenes.denseRealistic().scene(false, QualityTier.FULL);
		assertTrue(SceneBoxes.detailed(scene).size() <= ArcadeScene.MAX_DETAILED);
		assertEquals(null, SceneBoxes.firstOverlap(SceneBoxes.labels(scene)));
		assertTrue(!scene.withRole(Role.PREVIEW).isEmpty(), "the look-ahead is shown as previews");
	}

	@Test
	public void theReferencesShowWhatTheyAreMeantToShow() {
		Scene six = ReferenceScenes.sixStrings().scene(false, QualityTier.FULL);
		assertEquals(6, six.withRole(Role.STRING).size());
		assertTrue(!six.withRole(Role.BADGE).isEmpty() && !six.withRole(Role.CARTOUCHE_FRAME).isEmpty() && !six.withRole(Role.MARKER).isEmpty());
		Scene four = ReferenceScenes.fourStrings().scene(false, QualityTier.FULL);
		assertEquals(4, four.withRole(Role.STRING).size());
		assertTrue(!four.withRole(Role.BADGE).isEmpty() && !four.withRole(Role.MARKER).isEmpty());
		assertEquals(SceneCapture.of(six), SceneCapture.of(ReferenceScenes.sixStrings().scene(false, QualityTier.FULL)), "deterministic");
	}
}
