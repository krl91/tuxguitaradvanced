package app.tuxguitar.app.tools.custom.practice.arcade.view;

import java.util.Collections;
import java.util.List;

import app.tuxguitar.app.tools.custom.practice.session.FlameSetting;

/**
 * The demo fire of spec 018 drawn on its own (spec 019 FR-001, FR-002, FR-004, contracts/score-overlay.md): at the bottom of the visible score in Wait and
 * Follow, and the Arcade preview of the Advanced tab. The scene holds only the items of the fire, with no lane and no text, faded near the guard boxes it
 * is given. Pure.
 */
public final class ScoreFireScene {

	/** A score area fewer than this many cells high has room for the ember rows only (Edge Case "very small score area"). */
	static final int LEAST_ROWS = 8;

	private ScoreFireScene() {
	}

	/**
	 * The fire at the bottom of the score.
	 *
	 * <p>The height of the corners is that of the whole fire, ember rows included (FR-004: never above a share of the score area), where Arcade's
	 * tongues rise that share above the ember rows: the ember rows are taken out of the height the tongues are given.
	 *
	 * @param guard the boxes {x1, y1, x2, y2} the fire must not cover, in the coordinates of the viewport: the framed note, the next note and the palette
	 */
	public static Scene scene(Viewport viewport, QualityTier tier, FlameState flames, List<double[]> guard) {
		if (flames == null || flames.setting() == FlameSetting.OFF || tier == QualityTier.PERFORMANCE) {
			return new Scene(Collections.<SceneItem>emptyList(), flames != null ? flames.flameClockNanos() : 0L, viewport, Theme.DARK);
		}
		FireTuning tuning = flames.tuning();
		double cell = FireField.cell(viewport, tier, tuning);
		FlameState shown = flames;
		if (viewport.height() < LEAST_ROWS * cell) {
			shown = new FlameState(flames.setting(), 0.0, flames.flameClockNanos(), flames.motion(), flames.bursts(), tuning);
		}
		double embers = tuning.integer(FireParameter.EMBER_ROWS) * cell / viewport.height();
		FireTuning tongues = tuning.with(FireParameter.HEIGHT_CORNERS, tuning.value(FireParameter.HEIGHT_CORNERS) - embers);
		List<double[]> boxes = (guard != null ? guard : Collections.<double[]>emptyList());
		return new Scene(FlameDrawing.items(viewport, tier, shown, boxes, tongues), flames.flameClockNanos(), viewport, Theme.DARK);
	}

	/**
	 * The flames of an Arcade picture with no game (no lanes, no text), for the Arcade preview of the Advanced tab only: with Arcade's defaults they are the
	 * items of a real Arcade picture of this size, without the fading near its texts.
	 */
	public static Scene arcadePreview(Viewport viewport, QualityTier tier, FlameState flames) {
		if (flames == null || flames.setting() == FlameSetting.OFF || tier == QualityTier.PERFORMANCE) {
			return new Scene(Collections.<SceneItem>emptyList(), flames != null ? flames.flameClockNanos() : 0L, viewport, Theme.DARK);
		}
		return new Scene(FlameDrawing.items(viewport, tier, flames, Collections.<double[]>emptyList(), flames.tuning()), flames.flameClockNanos(), viewport,
				Theme.DARK);
	}
}
