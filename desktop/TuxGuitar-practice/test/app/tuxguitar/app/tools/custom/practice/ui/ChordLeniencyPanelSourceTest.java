package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

import org.junit.jupiter.api.Test;

/**
 * Where the chord check setting sits (spec 024, task T031; contracts/chord-leniency.md): in the <i>Mode</i> tab, on the row after {@code FollowPanel},
 * released with the window and never disabled, even during a run. Checked on the source, as {@code PracticeWindow} needs the whole application; the
 * panel itself is tested natively by {@link ChordLeniencyPanelSwtTest}.
 */
public class ChordLeniencyPanelSourceTest {

	@Test
	public void thePanelIsInTheModeTabAfterTheFollowPanelAndIsNeverDisabled() throws Exception {
		String source = new String(Files.readAllBytes(Paths.get("src/app/tuxguitar/app/tools/custom/practice/ui/PracticeWindow.java")), StandardCharsets.UTF_8);
		int follow = source.indexOf("this.followPanel = new FollowPanel(");
		int chord = source.indexOf("this.chordLeniencyPanel = new ChordLeniencyPanel(");
		int passages = source.indexOf("\"practice.tab.passages\"");
		assertTrue(follow > 0 && chord > follow && chord < passages, "the chord check is built in the Mode tab, after the FollowPanel");
		assertTrue(source.contains("new ChordLeniencyPanel(uiFactory, modePanel,"), "in the Mode tab's panel");
		assertTrue(source.contains("modeLayout.set(this.chordLeniencyPanel.getControl(), 2, 1,"), "on the row after the FollowPanel");
		assertTrue(source.contains("this.chordLeniencyPanel.dispose()"), "released with the window");
		assertFalse(source.contains("chordLeniencyPanel.setEnabled") || source.contains("chordLeniencyPanel.getControl().setEnabled"), "never disabled, even during a run");
	}

	@Test
	public void theLevelsAreNamedAsTheGuideNamesThemInThreeLanguages() throws Exception {
		String[][] names = {
				{ "", "Chord check", "Strict", "Normal", "Lenient", "Custom" },
				{ "_fr", "Vérification des accords", "Strict", "Normal", "Tolérant", "Personnalisé" },
				{ "_es", "Comprobación de acordes", "Estricto", "Normal", "Tolerante", "Personalizado" } };
		String[] levels = { "strict", "normal", "lenient", "custom" };
		for( String[] language : names ) {
			Properties messages = new Properties();
			try (Reader reader = new InputStreamReader(Files.newInputStream(Paths.get("share/lang/messages" + language[0] + ".properties")), StandardCharsets.UTF_8)) {
				messages.load(reader);
			}
			assertEquals(language[1], messages.getProperty("practice.chord-leniency"), "the label" + language[0]);
			for( int i = 0 ; i < levels.length ; i ++ ) {
				assertEquals(language[i + 2], messages.getProperty("practice.chord-leniency." + levels[i]), levels[i] + language[0]);
				assertNotNull(messages.getProperty("practice.chord-leniency." + levels[i] + ".line"), levels[i] + ".line" + language[0]);
			}
		}
	}
}
