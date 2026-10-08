package app.tuxguitar.app.tools.custom.practice.arcade.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeRun;
import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeVerdict;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneItem.Role;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;

/** Without colour (spec 003 T039, FR-031, SC-012): the string, the fret, the timing of a chord and the verdict are still told by position, text and symbol. */
public class ArcadeSceneMonochromeTest {

	private static final long MS = SceneFixture.MS;
	private static final Viewport SCREEN = new Viewport(1280, 720);

	private static SceneFixture song() {
		SongFixture song = new SongFixture(2);
		song.quarter(0, 0, 6, 3);
		song.quarter(0, 1, 3, 5);
		song.chord(0, 2, new int[] { 6, 5, 4 }, new int[] { 5, 7, 7 });
		song.quarter(0, 3, 1, 0);
		return new SceneFixture(song, SceneFixture.sixStrings(), 100);
	}

	@Test
	public void everyItemIsGreyAndOtherwiseTheSceneIsTheColourSceneItemForItem() {
		SceneFixture fixture = song();
		for (long offset = -2500; offset <= 400; offset += 150) {
			long now = fixture.onset(1) + offset * MS;
			ArcadeRun.View run = ArcadeSceneTest.viewOf(ArcadeVerdict.ON_TIME, ArcadeVerdict.LATE, ArcadeVerdict.INCOMPLETE, ArcadeVerdict.WAITING);
			List<SceneItem> colour = fixture.scene(false).build(now, SCREEN, run, 100).items();
			List<SceneItem> grey = fixture.scene(true).build(now, SCREEN, run, 100).items();
			assertEquals(colour.size(), grey.size());
			for (int i = 0; i < grey.size(); i++) {
				assertTrue(grey.get(i).isGrey(), "monochrome: " + grey.get(i));
				assertEquals(colour.get(i).role(), grey.get(i).role());
				assertEquals(colour.get(i).text(), grey.get(i).text());
				assertEquals(colour.get(i).valueCount(), grey.get(i).valueCount());
				for (int v = 0; v < grey.get(i).valueCount(); v++) {
					assertEquals(colour.get(i).value(v), grey.get(i).value(v), 0.0, "the geometry does not depend on colour");
				}
			}
		}
	}

	@Test
	public void theStringIsToldByItsLabelAndItsRowTheFretByItsPositionAndItsNumber() {
		SceneFixture fixture = song();
		Scene picture = fixture.scene(true).build(fixture.onset(1) - 400 * MS, SCREEN, null, 100);
		Set<String> labels = new HashSet<String>();
		Set<Double> rows = new HashSet<Double>();
		for (SceneItem label : picture.withRole(Role.STRING_LABEL)) {
			labels.add(label.text());
			rows.add(label.value(1));
		}
		assertEquals(6, labels.size(), "six labels, one per string");
		assertEquals(6, rows.size(), "six rows");
		SceneItem ring = picture.forEvent(1, Role.TARGET_RING).get(0);
		SceneItem string = null;
		for (SceneItem item : picture.withRole(Role.STRING_LABEL)) {
			if (item.tgString() == 3) {
				string = item;
			}
		}
		assertEquals(string.value(1), ring.value(1), 1e-9, "the target sits on the row of its string, whose label says which one");
		assertEquals("5", picture.forEvent(1, Role.MARKER_FRET).get(0).text());
		SceneItem fretFive = null;
		for (SceneItem number : picture.withRole(Role.FRET_NUMBER)) {
			if ("5".equals(number.text())) {
				fretFive = number;
			}
		}
		assertEquals(fretFive.value(0), ring.value(0), 1.0, "the target lies over the number of its fret");
	}

	@Test
	public void aChordTimingIsTheBarAndItsDiagramIsReadableWithoutColour() {
		SceneFixture fixture = song();
		Scene picture = fixture.scene(true).build(fixture.onset(2) - 600 * MS, SCREEN, null, 100);
		assertEquals(1, picture.forEvent(2, Role.TIMING_BAR).size());
		assertEquals("5fr", picture.forEvent(2, Role.CARTOUCHE_FRET_LABEL).get(0).text());
		assertEquals(3, picture.forEvent(2, Role.CARTOUCHE_DOT).size());
		Set<Double> columns = new HashSet<Double>();
		for (SceneItem dot : picture.forEvent(2, Role.CARTOUCHE_DOT)) {
			columns.add(dot.value(0));
		}
		assertEquals(3, columns.size(), "each dot is on its own string line, the strings being told apart by their place");
	}

	@Test
	public void everyVerdictHasASymbolAndAWordThatAreDifferentFromEachOther() {
		SceneFixture fixture = song();
		Set<String> symbols = new HashSet<String>();
		Set<String> words = new HashSet<String>();
		List<ArcadeVerdict> shown = new ArrayList<ArcadeVerdict>();
		for (ArcadeVerdict verdict : ArcadeVerdict.values()) {
			if (verdict == ArcadeVerdict.WAITING) {
				continue;
			}
			ArcadeRun.View run = ArcadeSceneTest.viewOf(verdict, ArcadeVerdict.WAITING, ArcadeVerdict.WAITING, ArcadeVerdict.WAITING);
			Scene picture = fixture.scene(true).build(fixture.onset(0) + 200 * MS, SCREEN, run, 100);
			SceneItem symbol = picture.forEvent(0, Role.BADGE_SYMBOL).get(0);
			SceneItem word = picture.forEvent(0, Role.BADGE_TEXT).get(0);
			assertTrue(!symbol.text().isEmpty() && !word.text().isEmpty(), verdict.name());
			assertTrue(symbols.add(symbol.text()), "symbol of " + verdict + " is unique");
			assertTrue(words.add(word.text()), "word of " + verdict + " is unique");
			shown.add(verdict);
		}
		assertEquals(ArcadeVerdict.values().length - 1, shown.size());
		assertNotEquals(ArcadeScene.verdictSymbol(ArcadeVerdict.EARLY), ArcadeScene.verdictSymbol(ArcadeVerdict.LATE), "early and late differ without colour");
	}
}
