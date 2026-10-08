package app.tuxguitar.app.tools.custom.practice.arcade.ui;

import app.tuxguitar.app.tools.custom.practice.arcade.view.QualityTier;
import app.tuxguitar.app.tools.custom.practice.arcade.view.Scene;
import app.tuxguitar.ui.resource.UIPainter;

/**
 * The selected renderer with its fallback behind it (spec 003 FR-057, SC-016, T046): when the selected path cannot paint (a resource refused, a painter
 * that lacks something), the frame is painted again by the fallback, which then stays in use; Arcade never fails to start or to paint because of the way
 * it draws. A forced fallback skips the selected path altogether. The scene, the clock and the judging know nothing of this choice.
 */
public final class GuardedRenderer extends PainterRenderer {

	private final PainterRenderer selected;
	private final PainterRenderer fallback;
	private boolean usingFallback;
	private String failure;

	/** @param forceFallback true to use the fallback from the first frame */
	public GuardedRenderer(PainterRenderer selected, PainterRenderer fallback, boolean forceFallback) {
		this.selected = selected;
		this.fallback = fallback;
		this.usingFallback = forceFallback;
	}

	@Override
	public void target(UIPainter painter) {
		this.selected.target(painter);
		this.fallback.target(painter);
	}

	@Override
	public void render(Scene scene, QualityTier tier) {
		if (!this.usingFallback) {
			try {
				this.selected.render(scene, tier);
				return;
			} catch (RuntimeException exception) {
				this.usingFallback = true;
				this.failure = String.valueOf(exception);
			}
		}
		this.fallback.render(scene, tier);
	}

	public boolean isUsingFallback() {
		return this.usingFallback;
	}

	/** Why the selected path was given up, or null. */
	public String failure() {
		return this.failure;
	}

	@Override
	public String name() {
		return this.usingFallback ? this.fallback.name() : this.selected.name();
	}

	@Override
	public void dispose() {
		this.selected.dispose();
		this.fallback.dispose();
	}
}
