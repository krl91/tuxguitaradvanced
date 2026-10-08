package app.tuxguitar.app.tools.custom.practice.arcade.view;

import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Writes the images of the reference scenes for the owner's review (spec 003 T044) when {@code -Dtuxguitar.arcade.images=<directory>} is given. */
public class ArcadeReferenceImagesTest {

	@Test
	public void writeTheImagesOfTheReferenceScenes() throws IOException {
		String directory = System.getProperty("tuxguitar.arcade.images");
		Assumptions.assumeTrue(directory != null && !directory.isEmpty(), "no directory asked for");
		System.setProperty("java.awt.headless", "true");
		for (ReferenceScenes.Reference reference : ReferenceScenes.all()) {
			String base = "arcade-" + reference.name;
			SceneImageWriter.write(reference.scene(false, QualityTier.FULL), new File(directory, base + "-full.png"));
			SceneImageWriter.write(reference.scene(false, QualityTier.PERFORMANCE), new File(directory, base + "-performance.png"));
			SceneImageWriter.write(reference.scene(true, QualityTier.FULL), new File(directory, base + "-monochrome-full.png"));
			SceneImageWriter.write(reference.scene(true, QualityTier.PERFORMANCE), new File(directory, base + "-monochrome-performance.png"));
			SceneImageWriter.write(reference.fixture.scene(false).withTheme(Theme.LIGHT).build(reference.nowNanos, ReferenceScenes.SCREEN, reference.run, 100, QualityTier.FULL),
					new File(directory, base + "-light-full.png"));
		}
	}
}
