package app.tuxguitar.app.tools.custom.practice.arcade.ui;

import app.tuxguitar.app.tools.custom.practice.arcade.view.QualityTier;
import app.tuxguitar.app.tools.custom.practice.arcade.view.Scene;

/**
 * The narrow, backend-neutral contract between the scene and whatever puts it on the screen (spec 003 FR-057, T041). The scene says what to draw and
 * where; a renderer only replays it. Nothing of the scene model, the camera, the clock or the judging depends on the renderer, so another backend can be
 * chosen later by measurement without touching them, and the baseline (the toolkit's painter) stays as the fallback with the same semantics.
 */
public interface ArcadeRenderer {

	/** Draws the scene at the quality tier. Called on the UI thread only; never blocks on anything but the painting itself. */
	void render(Scene scene, QualityTier tier);

	/** Releases what the renderer cached (colours, fonts). */
	void dispose();
}
