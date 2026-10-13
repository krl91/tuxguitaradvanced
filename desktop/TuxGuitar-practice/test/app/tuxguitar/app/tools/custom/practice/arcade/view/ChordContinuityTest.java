package app.tuxguitar.app.tools.custom.practice.arcade.view;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneItem.Role;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;

/** A chord comes in one move: from one instant to the next, nothing of its panel jumps (owner, 2026-10-13: a chord jumped onto the neck at the end of its approach). */
public class ChordContinuityTest {

	private static final long MS = SceneFixture.MS;
	private static final Viewport SCREEN = new Viewport(1280, 720);

	private static double[] corners(SceneItem quad) {
		double[] c = new double[8];
		for (int i = 0; i < 8; i++) {
			c[i] = quad.value(i);
		}
		return c;
	}

	@Test
	public void theCornersOfAChordPanelNeverJumpDuringItsApproach() {
		SongFixture song = new SongFixture(4);
		song.chord(0, 0, new int[] { 5, 4, 3, 2 }, new int[] { 3, 2, 0, 1 });
		song.quarter(0, 1, 2, 5);
		song.quarter(0, 2, 3, 7);
		song.quarter(0, 3, 4, 9);
		song.chord(1, 0, new int[] { 6, 5, 4 }, new int[] { 3, 2, 0 });
		song.quarter(1, 1, 1, 12);
		song.quarter(1, 2, 1, 10);
		song.chord(2, 0, new int[] { 6, 5, 4, 3 }, new int[] { 1, 1, 2, 3 });
		song.quarter(2, 1, 2, 5);
		song.chord(3, 0, new int[] { 6, 5, 4, 3, 2, 1 }, new int[] { 0, 2, 2, 1, 0, 0 });
		SceneFixture fixture = new SceneFixture(song, SceneFixture.sixStrings(), 100);
		long look = CameraTrack.Settings.standard().lookAheadNanos;
		StringBuilder report = new StringBuilder();
		double worst = 0.0;
		for (int event = 0; event < fixture.events.size(); event++) {
			double[] previous = null;
			double max = 0.0;
			long at = 0;
			for (long t = fixture.onset(event) - look; t <= fixture.onset(event); t += 5 * MS) {
				Scene scene = fixture.scene(false).build(t, SCREEN, null, 100, QualityTier.FULL);
				if (scene.forEvent(event, Role.CHORD_PANEL).isEmpty()) {
					previous = null;
					continue;
				}
				double[] now = corners(scene.forEvent(event, Role.CHORD_PANEL).get(0));
				if (previous != null) {
					for (int i = 0; i < 8; i++) {
						double step = Math.abs(now[i] - previous[i]);
						if (step > max) {
							max = step;
							at = t - fixture.onset(event);
						}
					}
				}
				previous = now;
			}
			report.append("event ").append(event).append(" max step ").append(String.format("%.1f", max)).append(" px at ").append(at / MS).append(" ms\n");
			worst = Math.max(worst, max);
		}
		assertTrue(worst < 30.0, "a corner moved " + worst + " px in 5 ms\n" + report);
	}
}
