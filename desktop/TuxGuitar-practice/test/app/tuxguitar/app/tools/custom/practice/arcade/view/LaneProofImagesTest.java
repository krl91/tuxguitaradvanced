package app.tuxguitar.app.tools.custom.practice.arcade.view;

import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.model.SongFixture;

/**
 * The simplest picture of the fret lanes (spec 003 T066), for the owner's eye before anything else: the fixed lane of each fret from 1 to 12 and a few notes
 * on them, among which two notes of the same fret on different strings. Written when {@code -Dtuxguitar.arcade.lanes=<file prefix>} is given; never in a build.
 */
public class LaneProofImagesTest {

	@Test
	public void writeTheLaneProofImages() throws IOException {
		String prefix = System.getProperty("tuxguitar.arcade.lanes");
		Assumptions.assumeTrue(prefix != null && !prefix.isEmpty(), "no file asked for");
		System.setProperty("java.awt.headless", "true");
		SongFixture song = new SongFixture(5);
		song.tempoChange(0, 240);
		// frets 1 to 12, one a quarter note, the fret 5 twice (low and high string), on a different string each time
		int[] frets = { 1, 2, 3, 4, 5, 5, 6, 7, 8, 9, 10, 11, 12 };
		int[] strings = { 6, 5, 4, 3, 6, 1, 2, 5, 4, 3, 2, 6, 1 };
		for (int i = 0; i < frets.length; i++) {
			song.quarter(1 + i / 4, i % 4, strings[i], frets[i]);
		}
		SceneFixture fixture = new SceneFixture(song, SceneFixture.sixStrings(), 100);
		Viewport screen = new Viewport(1280, 720);
		long[] moments = { fixture.onset(0) - 1700 * SceneFixture.MS, fixture.onset(4) - 900 * SceneFixture.MS, fixture.onset(5) - 40 * SceneFixture.MS };
		for (int i = 0; i < moments.length; i++) {
			Scene scene = fixture.scene(false).build(moments[i], screen, SceneBoxesAccess.completedBefore(i == 0 ? 0 : i == 1 ? 4 : 5, frets.length), 100, QualityTier.FULL);
			SceneImageWriter.write(scene, new File(prefix + "-" + (i + 1) + ".png"));
		}
	}
}
