package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.view.FireParameter;
import app.tuxguitar.app.tools.custom.practice.arcade.view.FireTuning;
import app.tuxguitar.app.tools.custom.practice.arcade.view.FlameState;
import app.tuxguitar.app.tools.custom.practice.arcade.view.QualityTier;
import app.tuxguitar.app.tools.custom.practice.arcade.view.ReferenceScenes;
import app.tuxguitar.app.tools.custom.practice.arcade.view.Scene;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneItem;
import app.tuxguitar.app.tools.custom.practice.arcade.view.ScoreFireScene;
import app.tuxguitar.app.tools.custom.practice.arcade.view.Viewport;
import app.tuxguitar.app.tools.custom.practice.session.FlameSetting;

/**
 * Spec 019 (T034, FR-016, FR-019, SC-011): every setting at its minimum and at its maximum, the others at their defaults, in both groups. The fire never
 * covers what must be read (the framed and next notes and the palette on the score, the texts of an Arcade picture), its cost stays capped, and nothing
 * throws. The judging at these extremes is checked by {@code session.FireExtremesVerdictsTest}.
 */
public class FireExtremesTest {

	private static final Viewport SCORE = new Viewport(1280, 800);
	private static final long CLOCK = 4_000_000_000L;

	private static List<FireTuning> extremes(FireTuning.Group group) {
		List<FireTuning> tunings = new ArrayList<FireTuning>();
		for (FireParameter parameter : FireParameter.values()) {
			if (parameter.in(group)) {
				tunings.add(FireTuning.defaults(group).with(parameter, parameter.min()));
				tunings.add(FireTuning.defaults(group).with(parameter, parameter.max()));
			}
		}
		return tunings;
	}

	private static List<FlameState.Burst> bursts() {
		return Arrays.asList(new FlameState.Burst(CLOCK - 600_000_000L, 12), new FlameState.Burst(CLOCK - 300_000_000L, 12), new FlameState.Burst(CLOCK - 50_000_000L, 12));
	}

	private static double[] box(SceneItem item) {
		double x1 = Double.MAX_VALUE;
		double y1 = Double.MAX_VALUE;
		double x2 = -Double.MAX_VALUE;
		double y2 = -Double.MAX_VALUE;
		for (int i = 0; i < 8; i += 2) {
			x1 = Math.min(x1, item.value(i));
			x2 = Math.max(x2, item.value(i));
			y1 = Math.min(y1, item.value(i + 1));
			y2 = Math.max(y2, item.value(i + 1));
		}
		return new double[] { x1, y1, x2, y2 };
	}

	private static boolean meets(double[] a, double[] b) {
		return a[0] < b[2] + 4 && a[2] > b[0] - 4 && a[1] < b[3] + 4 && a[3] > b[1] - 4;
	}

	/** The most items a fire can have: one per cell of the finest grid, and the most sparks. */
	private static int ceiling(Viewport viewport) {
		double finest = FireParameter.CELL_SMALLEST.min();
		return (int) (384 * Math.ceil(viewport.height() / finest)) + 1000;
	}

	@Test
	public void theScoreFireNeverCoversTheGuardBoxesAtAnyExtreme() {
		double w = SCORE.width();
		double h = SCORE.height();
		List<double[]> guard = Arrays.asList(new double[] { 20, h - 60, 70, h - 10 }, new double[] { w / 2 - 20, h - 50, w / 2 + 20, h - 5 },
				new double[] { w - 220, h - 90, w - 10, h - 20 }, new double[] { 300, h - 260, 360, h - 200 });
		for (FireTuning.Group group : FireTuning.Group.values()) {
			for (FireTuning tuning : extremes(group)) {
				for (FlameSetting setting : new FlameSetting[] { FlameSetting.FULL, FlameSetting.DISCREET }) {
					FlameState flames = new FlameState(setting, 1.0, CLOCK, true, bursts(), tuning);
					List<SceneItem> items = ScoreFireScene.scene(SCORE, QualityTier.FULL, flames, guard).items();
					assertTrue(items.size() <= ceiling(SCORE), tuning + ": " + items.size() + " items");
					for (SceneItem item : items) {
						if (item.alpha() <= 0.12) {
							continue;
						}
						for (double[] g : guard) {
							assertFalse(meets(box(item), g), group + " " + setting + " " + tuning + ": an item at " + item.alpha() + " meets a guard box");
						}
					}
				}
			}
		}
	}

	/** The boxes of the texts of an Arcade picture, as the reading guard of spec 009 counts them. */
	private static List<double[]> textBoxes(Scene scene) {
		List<double[]> boxes = new ArrayList<double[]>();
		for (SceneItem item : scene.items()) {
			if (item.shape() == SceneItem.Shape.TEXT && item.text() != null && !item.text().trim().isEmpty()) {
				double width = item.text().length() * item.value(2) * 0.66;
				double x = item.value(0);
				double left = item.align() == SceneItem.Align.CENTRE ? x - width / 2.0 : item.align() == SceneItem.Align.RIGHT ? x - width : x;
				boxes.add(new double[] { left, item.value(1) - item.value(2) * 0.6, left + width, item.value(1) + item.value(2) * 0.6 });
			} else if (item.role() == SceneItem.Role.BADGE) {
				boxes.add(box(item));
			}
		}
		return boxes;
	}

	@Test
	public void theArcadeFireNeverCoversATextAtAnyExtreme() {
		ReferenceScenes.Reference reference = ReferenceScenes.flameReading();
		for (FireTuning.Group group : FireTuning.Group.values()) {
			for (FireTuning tuning : extremes(group)) {
				FlameState flames = new FlameState(FlameSetting.FULL, 1.0, CLOCK, true, bursts(), tuning);
				Scene scene = reference.fixture.scene(false).build(reference.nowNanos, ReferenceScenes.SCREEN, reference.run, 100, QualityTier.FULL, flames);
				List<double[]> texts = textBoxes(scene);
				assertFalse(texts.isEmpty());
				int fire = 0;
				for (SceneItem item : scene.items()) {
					if (item.role() != SceneItem.Role.FLAME_EMBER && item.role() != SceneItem.Role.FLAME_TONGUE) {
						continue;
					}
					fire++;
					if (item.alpha() <= 0.12) {
						continue;
					}
					for (double[] text : texts) {
						assertFalse(meets(box(item), text), group + " " + tuning + ": a piece of fire at " + item.alpha() + " meets a text");
					}
				}
				assertTrue(fire <= ceiling(ReferenceScenes.SCREEN), tuning + ": " + fire + " items");
			}
		}
	}

	@Test
	public void theScoreFireRunsAtEveryExtremeWithoutThrowing() {
		for (FireTuning tuning : extremes(FireTuning.Group.SCORE)) {
			final long[] wall = { 1_000_000_000L };
			ScoreFire.Score port = new ScoreFire.Score() {
				public app.tuxguitar.ui.widget.UICanvas canvas() {
					return null;
				}

				public List<double[]> guardBoxes(app.tuxguitar.app.tools.custom.practice.model.PracticeEvent current,
						app.tuxguitar.app.tools.custom.practice.model.PracticeEvent next, PaletteDodge.Box palette) {
					return java.util.Collections.singletonList(new double[] { 600, 700, 680, 790 });
				}

				public void redraw(boolean overlayOnly) {
				}
			};
			ScoreFire.Ticker ticker = new ScoreFire.Ticker() {
				public void start(Runnable tick) {
				}

				public void stop() {
				}
			};
			ScoreFire fire = new ScoreFire(null, port, ticker, () -> wall[0], System::nanoTime);
			fire.start(FlameSetting.FULL, tuning, true);
			for (int streak = 1; streak <= 40; streak++) {
				wall[0] += 70_000_000L;
				fire.update(streak % 9 == 0 ? 0 : streak, streak, Arrays.asList(streak), streak % 13 == 0, null, null, null);
				fire.tick();
				fire.paint(null, 1280f, 800f);
			}
			assertTrue(fire.level() >= 0.0 && fire.level() <= 1.0, tuning.toString());
			fire.stop();
		}
	}
}
