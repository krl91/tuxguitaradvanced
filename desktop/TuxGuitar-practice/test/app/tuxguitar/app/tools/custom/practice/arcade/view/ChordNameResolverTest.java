package app.tuxguitar.app.tools.custom.practice.arcade.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.view.ChordNameResolver.Display;
import app.tuxguitar.app.tools.custom.practice.arcade.view.ChordNameResolver.Source;

/**
 * The name shown for a chord (spec 003 FR-061, SC-018): the written name wins, otherwise a name derived from the written notes with the chord database and
 * the naming convention of the application, otherwise the notes themselves; nothing is read from the sound, nothing is written to the score, and the same
 * input gives the same text.
 */
public class ChordNameResolverTest {

	private static final int[] OPEN = { 0, 64, 59, 55, 50, 45, 40 };

	/** The pitches of a shape written as {string, fret} pairs on a guitar in standard tuning. */
	private static int[] shape(int... stringFret) {
		int[] pitches = new int[stringFret.length / 2];
		for (int i = 0; i < pitches.length; i++) {
			pitches[i] = OPEN[stringFret[2 * i]] + stringFret[2 * i + 1];
		}
		return pitches;
	}

	private static String name(int[] pitches) {
		return ChordNameResolver.resolve(null, pitches, 0).text();
	}

	@Test
	public void aNameWrittenInTheScoreAlwaysWinsAndIsShownAsWrittenWhateverTheNotes() {
		Display display = ChordNameResolver.resolve("Fsus2/G", shape(5, 3, 4, 2, 3, 0, 2, 1, 1, 0), 0);
		assertEquals("Fsus2/G", display.text());
		assertEquals(Source.WRITTEN, display.source());
		assertEquals("A5", ChordNameResolver.resolve("  A5 ", shape(6, 0, 5, 0), 3).text(), "trimmed, nothing else");
		assertEquals(Source.DERIVED, ChordNameResolver.resolve("   ", shape(5, 3, 4, 2, 3, 0, 2, 1, 1, 0), 0).source(), "a blank name is no name");
		assertEquals(Source.DERIVED, ChordNameResolver.resolve("", shape(5, 3, 4, 2, 3, 0, 2, 1, 1, 0), 0).source());
		assertEquals(Source.DERIVED, ChordNameResolver.resolve(null, shape(5, 3, 4, 2, 3, 0, 2, 1, 1, 0), 0).source());
	}

	@Test
	public void thePowerChordOfTheFifthFretIsA5() {
		Display display = ChordNameResolver.resolve(null, shape(6, 5, 5, 7, 4, 7), 0);
		assertEquals("A5", display.text());
		assertEquals(Source.DERIVED, display.source());
	}

	@Test
	public void theOpenFormsAreNamed() {
		assertEquals("C", name(shape(5, 3, 4, 2, 3, 0, 2, 1, 1, 0)));
		assertEquals("Am", name(shape(5, 0, 4, 2, 3, 2, 2, 1, 1, 0)));
		assertEquals("G", name(shape(6, 3, 5, 2, 4, 0, 3, 0, 2, 0, 1, 3)));
		assertEquals("D", name(shape(4, 0, 3, 2, 2, 3, 1, 2)));
		assertEquals("E", name(shape(6, 0, 5, 2, 4, 2, 3, 1, 2, 0, 1, 0)));
		assertEquals("Em", name(shape(6, 0, 5, 2, 4, 2, 3, 0, 2, 0, 1, 0)));
	}

	@Test
	public void majorMinorAndDominantSeventhsAreNamed() {
		assertEquals("Cmaj7", name(shape(5, 3, 4, 2, 3, 0, 2, 0, 1, 0)));
		assertEquals("G7", name(shape(6, 3, 5, 2, 4, 0, 3, 0, 2, 0, 1, 1)));
		assertEquals("Am7", name(shape(5, 0, 4, 2, 3, 0, 2, 1, 1, 0)));
		assertEquals("Em7", name(shape(6, 0, 5, 2, 4, 0, 3, 0, 2, 0, 1, 0)));
		assertEquals("C6", name(new int[] { 48, 52, 55, 57 }));
	}

	@Test
	public void suspendedDiminishedAugmentedAndAddedNoteFormsAreNamed() {
		assertEquals("Dsus2", name(shape(4, 0, 3, 2, 2, 3, 1, 0)));
		assertEquals("Dsus4", name(shape(4, 0, 3, 2, 2, 3, 1, 3)));
		assertEquals("Bdim", name(new int[] { 47, 53, 59, 62 }));
		assertEquals("Caug", name(shape(5, 3, 4, 2, 3, 1, 2, 1, 1, 0)));
		assertEquals("Cadd9", name(shape(5, 3, 4, 2, 3, 0, 2, 3, 1, 0)));
	}

	@Test
	public void theLowestNoteNamesAnInversionWithASlash() {
		assertEquals("C/E", name(shape(6, 0, 5, 3, 4, 2, 3, 0, 2, 1, 1, 0)));
		assertEquals("G/B", name(shape(5, 2, 4, 0, 3, 0, 2, 0, 1, 3)));
		assertEquals("C", name(shape(5, 3, 4, 2, 3, 0, 2, 1, 1, 0)), "the same notes with the tonic in the bass: no slash");
	}

	@Test
	public void doubledNotesNeverChangeTheName() {
		int[] plain = { 48, 52, 55 };
		int[] doubled = { 48, 52, 55, 60, 64, 67, 72, 48 };
		assertEquals(name(plain), name(doubled));
		assertEquals("A5", name(new int[] { 45, 52, 57, 64, 69 }));
	}

	@Test
	public void theSpellingFollowsTheKeyAndIsStableInTheKeyOfC() {
		int[] bFlat = { 58, 62, 65 };
		assertEquals("Bb", ChordNameResolver.resolve(null, bFlat, 0).text());
		assertEquals("Bb", ChordNameResolver.resolve(null, bFlat, 8).text(), "a flat key");
		assertEquals("A#", ChordNameResolver.resolve(null, bFlat, 3).text(), "a sharp key spells it with a sharp, bass included");
		int[] fSharpMinor = { 54, 57, 61 };
		assertEquals("F#m", ChordNameResolver.resolve(null, fSharpMinor, 2).text());
		assertEquals("F#m", ChordNameResolver.resolve(null, fSharpMinor, 0).text());
		assertEquals("Gbm", ChordNameResolver.resolve(null, fSharpMinor, 9).text(), "a flat key");
		int[] dFlat = { 49, 53, 56 };
		assertEquals("Db", ChordNameResolver.resolve(null, dFlat, 0).text(), "a major chord on the black key is spelled flat in the key of C");
		assertEquals("C#m", ChordNameResolver.resolve(null, new int[] { 49, 52, 56 }, 0).text(), "and a minor one sharp");
		assertEquals("C#", ChordNameResolver.resolve(null, dFlat, 4).text());
		for (int key = 0; key < 15; key++) {
			assertEquals(ChordNameResolver.resolve(null, dFlat, key), ChordNameResolver.resolve(null, dFlat, key), "stable");
		}
	}

	@Test
	public void aSetOfNotesNoTemplateDescribesIsListedFromTheLowestUp() {
		Display display = ChordNameResolver.resolve(null, new int[] { 48, 49, 50 }, 0);
		assertEquals(Source.PITCH_SET, display.source());
		assertEquals("C–C♯–D", display.text());
		assertEquals("C–D♭–D", ChordNameResolver.resolve(null, new int[] { 50, 48, 49 }, 8).text(), "from the lowest up, spelled for the flat key: D, C, Db by pitch is C, Db, D");
		Display cluster = ChordNameResolver.resolve(null, new int[] { 45, 48, 54, 55, 58 }, 0);
		assertEquals(Source.PITCH_SET, cluster.source());
		assertTrue(cluster.text().contains("–"));
		assertFalse(cluster.text().equals("Chord"));
		// one note, doubled in octaves, is only that note
		assertEquals("E", ChordNameResolver.resolve(null, new int[] { 40, 52, 64 }, 0).text());
	}

	@Test
	public void theSameInputGivesTheSameTextWhateverTheOrderOfTheNotes() {
		int[] a = shape(5, 3, 4, 2, 3, 0, 2, 1, 1, 0);
		int[] b = { a[4], a[0], a[3], a[1], a[2] };
		assertEquals(ChordNameResolver.resolve(null, a, 0), ChordNameResolver.resolve(null, b, 0));
		assertEquals(ChordNameResolver.resolve(null, a, 0), ChordNameResolver.resolve(null, a, 0));
	}

	@Test
	public void theResolverKnowsNothingOfTheSoundOfARunOrOfTheDetection() throws Exception {
		String source = new String(Files.readAllBytes(Paths.get("src/app/tuxguitar/app/tools/custom/practice/arcade/view/ChordNameResolver.java")), StandardCharsets.UTF_8);
		for (String forbidden : new String[] { "audio.engine", "AnalysisFrame", "FollowSession", "NoteInterpreter", "TargetEvidence", "PitchReading", "ChordMatcher", "detected" }) {
			assertFalse(source.replaceAll("(?s)/\\*.*?\\*/", "").contains(forbidden), "the resolver must not use " + forbidden);
		}
		assertNotEquals(-1, source.indexOf("TGChordDatabase"), "it uses the chord database of the application");
		assertNotEquals(-1, source.indexOf("TGChordNamingConvention"), "and its naming convention");
	}

	@Test
	public void aSeventhChordWithoutItsFifthIsStillNamedAndTheMoreUsualTemplateWinsATie() {
		assertEquals("G7", name(new int[] { 43, 47, 53 }), "G, B and F: a dominant seventh without its fifth");
		assertEquals(Source.DERIVED, ChordNameResolver.resolve(null, new int[] { 43, 47, 53 }, 0).source());
		// A, C, E and G with E in the bass is as much C6/E as Am7/E: the database lists the sixth before the minor seventh, and that order is the tie-break
		assertEquals("C6/E", name(new int[] { 40, 45, 48, 55 }));
	}
}
