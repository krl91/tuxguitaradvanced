package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.UITextClipboard;

/**
 * Spec 015 (T018, FR-013) on the real SWT toolkit: the SWT factory offers the text clipboard and the line it copies is the one found on the system clipboard.
 * Runs only with the profile {@code swt-gui-tests}. The previous content of the clipboard is restored.
 */
@Tag("swt-gui")
public class AboutClipboardSwtTest {

	@Test
	public void theSwtFactoryCopiesATextToTheSystemClipboard() throws Exception {
		Class<?> displayClass = Class.forName("org.eclipse.swt.widgets.Display");
		Object display = displayClass.getConstructor().newInstance();
		try {
			UIFactory factory = (UIFactory) Class.forName("app.tuxguitar.ui.swt.SWTFactory").getConstructor(displayClass).newInstance(display);
			assertTrue(factory instanceof UITextClipboard, "the SWT factory implements UITextClipboard");
			String line = "TuxGuitarAdvanced 0.6.0-test (fc50a2f43aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa)";
			Class<?> clipboardClass = Class.forName("org.eclipse.swt.dnd.Clipboard");
			Class<?> transferClass = Class.forName("org.eclipse.swt.dnd.TextTransfer");
			Object transfer = transferClass.getMethod("getInstance").invoke(null);
			Object clipboard = clipboardClass.getConstructor(displayClass).newInstance(display);
			Object before = clipboardClass.getMethod("getContents", Class.forName("org.eclipse.swt.dnd.Transfer")).invoke(clipboard, transfer);
			try {
				assertTrue(((UITextClipboard) factory).copyText(line), "copyText reports success");
				Object read = clipboardClass.getMethod("getContents", Class.forName("org.eclipse.swt.dnd.Transfer")).invoke(clipboard, transfer);
				assertEquals(line, read);
			} finally {
				if (before instanceof String) {
					((UITextClipboard) factory).copyText((String) before);
				}
				clipboardClass.getMethod("dispose").invoke(clipboard);
			}
		} finally {
			displayClass.getMethod("dispose").invoke(display);
		}
	}
}
