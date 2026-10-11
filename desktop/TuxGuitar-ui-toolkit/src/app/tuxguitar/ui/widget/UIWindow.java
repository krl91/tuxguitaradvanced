package app.tuxguitar.ui.widget;

import app.tuxguitar.ui.event.UICloseListener;
import app.tuxguitar.ui.resource.UIImage;

public interface UIWindow extends UILayoutContainer {

	String getText();

	void setText(String text);

	UIImage getImage();

	void setImage(UIImage image);

	void setMinimumSize(int width, int height);

	void open();

	void close();

	void minimize();

	void maximize();

	boolean isMaximized();

	/** The window fills the whole screen; a toolkit without a full screen mode maximizes it. */
	default void setFullScreen(boolean fullScreen) {
		if(fullScreen) {
			maximize();
		}
	}

	void moveToTop();

	void addCloseListener(UICloseListener listener);

	void removeCloseListener(UICloseListener listener);
}
