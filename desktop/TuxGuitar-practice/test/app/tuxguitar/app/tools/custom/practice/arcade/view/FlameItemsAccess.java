package app.tuxguitar.app.tools.custom.practice.arcade.view;

import java.util.Collections;
import java.util.List;

/** A public door to the items of the demo fire, for the tests of other packages (spec 018 T031). */
public final class FlameItemsAccess {

	private FlameItemsAccess() {
	}

	/** A scene made of the fire alone, as the scene puts it before every other item, with no text to fade near. */
	public static Scene fireAlone(Viewport viewport, QualityTier tier, FlameState flames) {
		List<SceneItem> items = FlameDrawing.items(viewport, tier, flames, Collections.<double[]>emptyList());
		return new Scene(items, flames.flameClockNanos(), viewport, Theme.DARK);
	}
}
