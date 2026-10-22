package app.tuxguitar.ui;

/**
 * Optional capability of a {@link UIFactory}: putting a text on the system clipboard. A toolkit that cannot do it does not implement it (spec 015, FR-013).
 */
public interface UITextClipboard {

	/** @return true when the text is on the clipboard */
	boolean copyText(String text);
}
