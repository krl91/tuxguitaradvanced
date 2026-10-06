package app.tuxguitar.app.tools.custom.practice.dataset;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** The reading of the annotations of a dataset (no dataset needed): what is a strummed chord, and what is rejected, with its reason. */
public class GuitarSetChordsTest {

	private static GuitarSetChords.Note note(int string, double time, double duration, int midi) {
		return new GuitarSetChords.Note(string, time, duration, midi);
	}

	private static final List<double[]> SPAN = Collections.singletonList(new double[] { 0.0, 100.0 });

	private static GuitarSetChords.Extraction strums(String label, GuitarSetChords.Note... notes) {
		return GuitarSetChords.strums("rec", Arrays.asList(notes), SPAN, Collections.singletonList(label));
	}

	@Test
	public void threeStringsStruckTogetherAreAChordWithTheirPitches() {
		GuitarSetChords.Extraction e = strums("A:min", note(1, 1.00, 0.5, 45), note(2, 1.02, 0.5, 52), note(3, 1.04, 0.5, 57));
		assertEquals(1, e.chords.size());
		assertArrayEquals(new int[] { 45, 52, 57 }, e.chords.get(0).pitches);
		assertEquals("min", e.chords.get(0).family);
		assertEquals(1.02, e.chords.get(0).onset, 1e-9);
		assertTrue(e.exclusions.isEmpty());
	}

	@Test
	public void twoNotesAreNotAChordAndAreNotCountedAsExcluded() {
		assertTrue(strums("A:min", note(1, 1.0, 0.5, 45), note(2, 1.02, 0.5, 52)).chords.isEmpty());
	}

	@Test
	public void aNoteOutsideTheInstructedChordRejectsTheStrumWithAReason() {
		GuitarSetChords.Extraction e = strums("A:min", note(1, 1.00, 0.5, 45), note(2, 1.02, 0.5, 52), note(3, 1.04, 0.5, 58));
		assertTrue(e.chords.isEmpty());
		assertEquals(Integer.valueOf(1), e.exclusions.get("a played note is not in the instructed chord (annotation disagreement)"));
	}

	@Test
	public void aStrumFollowedTooSoonByAnotherIsRejected() {
		GuitarSetChords.Extraction e = strums("A:min", note(1, 1.00, 0.5, 45), note(2, 1.02, 0.5, 52), note(3, 1.04, 0.5, 57), note(4, 1.20, 0.5, 60));
		assertTrue(e.chords.isEmpty());
		assertEquals(1, e.exclusions.size());
		assertTrue(e.exclusions.keySet().iterator().next().startsWith("next strike within"));
	}

	@Test
	public void aShortNoteTwoStringsOnTheSamePitchAndAnUnknownLabelAreRejected() {
		assertTrue(strums("A:min", note(1, 1.00, 0.1, 45), note(2, 1.02, 0.5, 52), note(3, 1.04, 0.5, 57)).exclusions.containsKey("a note is shorter than 0.2 s"));
		assertTrue(strums("A:min", note(1, 1.00, 0.5, 45), note(2, 1.02, 0.5, 52), note(3, 1.04, 0.5, 45)).exclusions.containsKey("two strings sound the same pitch"));
		assertTrue(strums("N", note(1, 1.00, 0.5, 45), note(2, 1.02, 0.5, 52), note(3, 1.04, 0.5, 57)).exclusions.containsKey("instructed label missing or not in the table of plain chords"));
	}

	@Test
	public void aStringStruckAgainWhileItRingsIsMarkedAsRinging() {
		// A2 rings from 0.2 to 1.5; the chord struck at 1.0 contains A2 again
		GuitarSetChords.Extraction e = strums("A:min", note(1, 0.20, 1.3, 45), note(1, 1.00, 0.5, 45), note(2, 1.02, 0.5, 52), note(3, 1.04, 0.5, 57));
		assertEquals(1, e.chords.size());
		assertEquals(1.0 / 3.0, e.chords.get(0).ringingShare, 1e-9);
	}

	@Test
	public void theLabelTable() {
		assertArrayEquals(new int[] { 3, 7, 10 }, GuitarSetChords.tonesOf("D#:maj"));
		assertArrayEquals(new int[] { 9, 0, 4 }, GuitarSetChords.tonesOf("A:min"));
		assertNull(GuitarSetChords.tonesOf("D#:sus2(7)/1"));
		assertNull(GuitarSetChords.tonesOf("N"));
		assertEquals("maj7", GuitarSetChords.qualityOf("C:maj7"));
	}

	@Test
	public void theJsonReaderReadsWhatTheAnnotationsContain() {
		@SuppressWarnings("unchecked")
		Map<String, Object> map = (Map<String, Object>) MiniJson.parse("{\"a\": [1, 2.5e1, -3], \"b\": {\"c\": \"x\\u0041\\n\", \"d\": null, \"e\": true}}");
		assertEquals(3, MiniJson.array(map.get("a")).size());
		assertEquals(25.0, MiniJson.array(map.get("a")).get(1));
		assertEquals("xA\n", MiniJson.object(map.get("b")).get("c"));
		assertNull(MiniJson.object(map.get("b")).get("d"));
		assertEquals(Boolean.TRUE, MiniJson.object(map.get("b")).get("e"));
		List<String> bad = new ArrayList<String>();
		for( String text : new String[] { "{", "[1,", "{\"a\" 1}", "[1] x" } ) {
			try {
				MiniJson.parse(text);
			} catch (RuntimeException exception) {
				bad.add(text);
			}
		}
		assertEquals(4, bad.size());
	}
}
