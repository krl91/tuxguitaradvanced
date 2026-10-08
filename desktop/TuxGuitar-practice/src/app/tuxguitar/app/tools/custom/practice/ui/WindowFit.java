package app.tuxguitar.app.tools.custom.practice.ui;

import app.tuxguitar.ui.resource.UIRectangle;
import app.tuxguitar.ui.resource.UISize;
import app.tuxguitar.ui.widget.UIWindow;

/**
 * Keeps a practice window's controls visible after its content changes (a new input hint,
 * level guidance, another feedback line): the packed size is recomputed, applied as the
 * minimum size, the window grows when it is smaller than its content (it never shrinks below
 * what the learner chose), and it is moved back inside the reference area. The abstract UI
 * toolkit exposes no display bounds, so the reference area is the TuxGuitar main window, which
 * lies on the current display.
 */
final class WindowFit {

	private WindowFit() {
	}

	/** Recomputes and applies the packed/minimum size of the window, then lays it out. */
	static void refit(UIWindow window, UIRectangle area) {
		if( window == null || window.isDisposed() ) {
			return;
		}
		window.computePackedSize(null, null);
		UISize packed = window.getPackedSize();
		window.setMinimumSize(Math.round(packed.getWidth()), Math.round(packed.getHeight()));
		UIRectangle current = window.getBounds();
		UIRectangle fitted = fit(current, packed, area);
		if( !same(current, fitted) ) {
			window.setBounds(fitted);
		} else {
			window.layout();
		}
	}

	/**
	 * The bounds that show the whole content: at least the packed size (never smaller than
	 * the current size), positioned inside {@code area} where it fits (top-left aligned with
	 * the area when it is larger). The size is never capped: hiding controls is worse than
	 * extending past the reference area.
	 */
	static UIRectangle fit(UIRectangle current, UISize packed, UIRectangle area) {
		float width = Math.max(current.getWidth(), packed.getWidth());
		float height = Math.max(current.getHeight(), packed.getHeight());
		float x = current.getX();
		float y = current.getY();
		if( area != null && area.getWidth() > 0 && area.getHeight() > 0 ) {
			x = clamp(x, area.getX(), area.getX() + area.getWidth() - width);
			y = clamp(y, area.getY(), area.getY() + area.getHeight() - height);
		}
		return new UIRectangle(x, y, width, height);
	}

	private static float clamp(float value, float min, float max) {
		// when the window is larger than the area, keep its top-left corner visible
		return Math.max(min, Math.min(value, Math.max(min, max)));
	}

	private static boolean same(UIRectangle a, UIRectangle b) {
		return Math.round(a.getX()) == Math.round(b.getX()) && Math.round(a.getY()) == Math.round(b.getY())
				&& Math.round(a.getWidth()) == Math.round(b.getWidth()) && Math.round(a.getHeight()) == Math.round(b.getHeight());
	}
}
