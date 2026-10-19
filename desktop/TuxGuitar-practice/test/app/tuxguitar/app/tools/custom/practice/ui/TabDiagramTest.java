package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.calibration.CalibrationPiece;
import app.tuxguitar.app.tools.custom.practice.ui.TabDiagram.Label;
import app.tuxguitar.app.tools.custom.practice.ui.TabDiagram.Layout;
import app.tuxguitar.app.tools.custom.practice.ui.TabDiagram.Mark;
import app.tuxguitar.ui.resource.UIPainter;

/**
 * Spec 013 (T058, FR-021): the tablature picture of a sound. The model (the marks of a note or of a chord), the geometry (six lines, the thinnest string on
 * top, a number on the string of each note, a chord in one column) and the drawing, checked with a recording painter.
 */
public class TabDiagramTest {

	private static final float W = 300f;
	private static final float H = 120f;

	private static List<Mark> one(int string, int fret) {
		return Arrays.asList(new Mark(string, fret));
	}

	@Test
	public void withNoMarkThereAreSixLinesFullWidthTheThinnestStringOnTop() {
		Layout layout = TabDiagram.layout(W, H, new ArrayList<Mark>());
		assertEquals(6, layout.lines.size());
		assertTrue(layout.labels.isEmpty());
		for (int i = 0; i < 6; i++) {
			float[] line = layout.lines.get(i);
			assertEquals(line[1], line[3], 1e-4, "horizontal");
			assertTrue(line[0] < 30f && line[2] > W - 30f, "almost the full width");
			if (i > 0) {
				assertTrue(line[1] > layout.lines.get(i - 1)[1], "string " + (i + 1) + " is below string " + i);
			}
		}
		assertTrue(layout.lines.get(0)[1] > 0f && layout.lines.get(5)[1] < H);
	}

	@Test
	public void aNoteHasItsFretNumberOnItsStringWhichIsCutAroundTheNumber() {
		Layout layout = TabDiagram.layout(W, H, one(5, 2));
		assertEquals(1, layout.labels.size());
		Label label = layout.labels.get(0);
		assertEquals("2", label.text);
		float y5 = TabDiagram.stringY(5, H);
		assertEquals(y5, label.y, 1e-4);
		assertEquals(W / 2f, label.x, 1e-4);
		int onString5 = 0;
		for (float[] line : layout.lines) {
			if (Math.abs(line[1] - y5) < 1e-4) {
				onString5++;
				assertTrue(line[2] < label.x || line[0] > label.x, "no line goes through the number");
			}
		}
		assertEquals(2, onString5, "the string is drawn in two pieces");
		assertEquals(7, layout.lines.size(), "the five other strings whole, this one in two");
	}

	@Test
	public void anOpenStringShowsZero() {
		assertEquals("0", TabDiagram.layout(W, H, one(6, 0)).labels.get(0).text);
	}

	@Test
	public void aChordHasOneNumberPerStringInTheSameColumn() {
		List<Mark> chord = Arrays.asList(new Mark(6, 0), new Mark(5, 2), new Mark(4, 2), new Mark(3, 1), new Mark(2, 0), new Mark(1, 0));
		Layout layout = TabDiagram.layout(W, H, chord);
		assertEquals(6, layout.labels.size());
		for (Label label : layout.labels) {
			assertEquals(W / 2f, label.x, 1e-4);
		}
		assertEquals(12, layout.lines.size(), "every string is cut once");
	}

	@Test
	public void everySoundOfTheShippedPieceGivesMarksOnStringsOneToSix() {
		CalibrationPiece piece = CalibrationPiece.load();
		for (int event = 0; event < piece.eventCount(); event++) {
			List<Mark> marks = TabDiagram.marks(piece.eventNotes(event));
			assertEquals(piece.eventNotes(event).size(), marks.size());
			assertEquals(piece.isChord(event), marks.size() > 1);
			for (Mark mark : marks) {
				assertTrue(mark.getString() >= 1 && mark.getString() <= 6, "string " + mark.getString());
				assertTrue(mark.getFret() >= 0);
			}
		}
	}

	@Test
	public void theDrawingTracesTheLinesAndWritesTheNumbers() {
		List<String> calls = new ArrayList<String>();
		UIPainter painter = (UIPainter) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { UIPainter.class }, (proxy, method, args) -> {
			if (method.getName().equals("drawString")) {
				calls.add("text:" + args[0]);
			} else if (method.getName().equals("lineTo")) {
				calls.add("line");
			}
			Class<?> type = method.getReturnType();
			return type == float.class ? Float.valueOf(10f) : type == boolean.class ? Boolean.FALSE : null;
		});
		new TabDiagram.Drawing(null, null).draw(painter, W, H, Arrays.asList(new Mark(5, 2), new Mark(4, 4)));
		assertEquals(Arrays.asList("text:4", "text:2"), calls.stream().filter(c -> c.startsWith("text:")).collect(java.util.stream.Collectors.toList()));
		assertEquals(8, calls.stream().filter(c -> c.equals("line")).count(), "four whole strings and two cut in two");
	}
}
