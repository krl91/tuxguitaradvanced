package app.tuxguitar.app.tools.custom.practice.arcade.ui;

import app.tuxguitar.ui.resource.UIPainter;

/** A renderer that replays a scene on the painter of the paint event being handled, which the canvas hands over before each render. */
public abstract class PainterRenderer implements ArcadeRenderer {

	/** The painter of the paint event being handled. */
	public abstract void target(UIPainter painter);

	/** The name of the path the last scene went through, for the reports. */
	public abstract String name();
}
