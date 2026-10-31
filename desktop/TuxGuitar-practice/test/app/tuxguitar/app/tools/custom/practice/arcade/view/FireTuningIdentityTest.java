package app.tuxguitar.app.tools.custom.practice.arcade.view;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.session.FlameSetting;

/**
 * Spec 019 (T006, FR-017, SC-010): with the defaults, the fire is exactly the fire of before the tuning, to the last bit of every coordinate, colour and
 * opacity, and the reference scene of spec 018 is unchanged.
 */
public class FireTuningIdentityTest {

	private static final Viewport[] SHAPES = { new Viewport(1280, 720), new Viewport(800, 600), new Viewport(640, 480), new Viewport(1500, 500),
			new Viewport(2000, 1000), new Viewport(1100, 950), new Viewport(900, 400) };

	/** Every field of every item, exactly. */
	static String exact(List<SceneItem> items) {
		StringBuilder text = new StringBuilder();
		for (SceneItem item : items) {
			text.append(item.shape()).append(' ').append(item.role());
			for (int i = 0; i < item.valueCount(); i++) {
				text.append(' ').append(Double.toString(item.value(i)));
			}
			text.append(' ').append(Integer.toHexString(item.colour())).append(' ').append(Double.toString(item.alpha())).append(' ').append(item.tgString())
					.append('\n');
		}
		return text.toString();
	}

	private static List<FlameState.Burst> bursts(int count) {
		List<FlameState.Burst> bursts = new ArrayList<FlameState.Burst>();
		for (int i = 0; i < count; i++) {
			bursts.add(new FlameState.Burst(3_600_000_000L + i * 70_000_000L, 1 + 3 * i));
		}
		return bursts;
	}

	@Test
	public void theDefaultsGiveTheSameItemsAsTheOldEntryPoint() {
		List<double[]> texts = Arrays.asList(new double[] { 300, 600, 420, 640 }, new double[] { 20, 680, 120, 715 });
		for (Viewport viewport : SHAPES) {
			for (QualityTier tier : new QualityTier[] { QualityTier.FULL, QualityTier.BALANCED }) {
				for (FlameSetting setting : new FlameSetting[] { FlameSetting.FULL, FlameSetting.DISCREET }) {
					for (double level : new double[] { 0.0, 0.3, 1.0 }) {
						for (boolean motion : new boolean[] { true, false }) {
							for (int count : new int[] { 0, 5 }) {
								FlameState old = new FlameState(setting, level, 4_000_000_000L, motion, bursts(count));
								FlameState tuned = new FlameState(setting, level, 4_000_000_000L, motion, bursts(count), FireTuning.ARCADE_DEFAULTS);
								String what = viewport.width() + "x" + viewport.height() + " " + tier + " " + setting + " " + level + " " + motion + " " + count;
								assertEquals(exact(FlameDrawing.items(viewport, tier, old, texts)),
										exact(FlameDrawing.items(viewport, tier, tuned, texts, FireTuning.ARCADE_DEFAULTS)), what);
								assertEquals(exact(FlameDrawing.items(viewport, tier, old, texts)), exact(FlameDrawing.items(viewport, tier, tuned, texts)), what);
							}
						}
					}
				}
			}
		}
	}

	@Test
	public void theReferenceSceneBuiltWithTheDefaultsIsTheFileOfSpec018() throws IOException {
		ReferenceScenes.Reference reference = ReferenceScenes.flameReading();
		FlameState flames = reference.flames;
		FlameState tuned = new FlameState(flames.setting(), flames.level(), flames.flameClockNanos(), flames.motion(), flames.bursts(), FireTuning.ARCADE_DEFAULTS);
		ReferenceScenes.Reference withDefaults = new ReferenceScenes.Reference(reference.name, reference.fixture, reference.tick, reference.run, tuned);
		assertEquals(ArcadeReferenceSceneTest.expected("flame-reading"), SceneCapture.of(withDefaults.scene(false, QualityTier.FULL)),
				"the reference file is read, never written again");
	}
}
