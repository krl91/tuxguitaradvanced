package app.tuxguitar.app.tools.custom.practice.arcade.ui;

import java.lang.reflect.Method;

/**
 * The refresh rate of the display, read through the toolkit (spec 023, research R7): on macOS the main screen's {@code maximumFramesPerSecond} (120 on a
 * ProMotion MacBook, 60 on most external screens), reached through SWT's Cocoa bindings by reflection, since this module is compiled against the abstract
 * toolkit only. Elsewhere, or when the toolkit cannot say, the rate is unknown and Arcade keeps today's 60 Hz periods. UI thread only (Cocoa wants it).
 */
public final class DisplayRate {

	/** No rate known. */
	public static final double UNKNOWN = 0.0;

	private DisplayRate() {
	}

	/** The rate of the main screen in frames a second, or {@link #UNKNOWN}. */
	public static double current() {
		try {
			Class<?> screens = Class.forName("org.eclipse.swt.internal.cocoa.NSScreen");
			Object screen = screens.getMethod("mainScreen").invoke(null);
			if( screen == null ) {
				return UNKNOWN;
			}
			long id = screens.getField("id").getLong(screen);
			Class<?> os = Class.forName("org.eclipse.swt.internal.cocoa.OS");
			long selector = (Long) os.getMethod("sel_registerName", String.class).invoke(null, "maximumFramesPerSecond");
			long responds = os.getField("sel_respondsToSelector_").getLong(null);
			Method send2 = os.getMethod("objc_msgSend", long.class, long.class);
			Method send3 = os.getMethod("objc_msgSend", long.class, long.class, long.class);
			// the selector exists from macOS 12: asked first, an unknown selector would end the process
			if( id == 0L || (((Long) send3.invoke(null, id, responds, selector)) & 0xFFL) == 0L ) {
				return UNKNOWN;
			}
			long rate = (Long) send2.invoke(null, id, selector);
			return rate > 0L && rate <= 1000L ? (double) rate : UNKNOWN;
		} catch( ReflectiveOperationException | LinkageError | RuntimeException exception ) {
			return UNKNOWN;
		}
	}
}
