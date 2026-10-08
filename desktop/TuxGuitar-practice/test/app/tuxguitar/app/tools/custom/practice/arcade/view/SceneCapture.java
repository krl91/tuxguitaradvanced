package app.tuxguitar.app.tools.custom.practice.arcade.view;

import java.util.Locale;

/** The structure of a scene as text, one line per item, coordinates to a tenth of a pixel: what the tests compare to a reference. */
public final class SceneCapture {

	private SceneCapture() {
	}

	public static String of(Scene scene) {
		StringBuilder text = new StringBuilder();
		text.append("viewport ").append(Math.round(scene.viewport().width())).append('x').append(Math.round(scene.viewport().height())).append('\n');
		for (SceneItem item : scene.items()) {
			text.append(item.shape()).append(' ').append(item.role());
			for (int i = 0; i < item.valueCount(); i++) {
				text.append(String.format(Locale.ROOT, " %.1f", item.value(i)));
			}
			text.append(String.format(Locale.ROOT, " #%06X a%.2f", item.colour(), item.alpha()));
			if (item.lineWidth() > 0) {
				text.append(String.format(Locale.ROOT, " w%.1f", item.lineWidth()));
			}
			if (item.eventIndex() >= 0) {
				text.append(" e").append(item.eventIndex());
			}
			if (item.tgString() > 0) {
				text.append(" s").append(item.tgString());
			}
			if (item.text() != null) {
				text.append(" \"").append(item.text()).append('"');
				if (item.align() != null) {
					text.append(' ').append(item.align());
				}
			}
			text.append('\n');
		}
		return text.toString();
	}
}
