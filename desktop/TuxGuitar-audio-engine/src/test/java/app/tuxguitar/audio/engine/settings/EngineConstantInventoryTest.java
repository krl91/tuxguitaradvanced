package app.tuxguitar.audio.engine.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Every number of the audio engine is accounted for (spec 026, T029, FR-002, FR-008, research R9): a numeric constant or a
 * {@code Parameters} default is a parameter's default (a parameter row of {@code inventory.md}) or a fixed constant listed with its kind and
 * reason in section 6 of {@code docs/architecture/recognition-engines.md}; a number written inline is mathematical or listed in the
 * inventory. {@code monitor/} (the amplifier of spec 022) is not recognition and is left out. The rule is in {@link ConstantInventory}.
 */
public class EngineConstantInventoryTest {

	private static final Path SOURCES = Paths.get("src/main/java/app/tuxguitar/audio/engine");

	@Test
	public void everyEngineNumberIsAParameterOrAFixedConstant() throws Exception {
		assertTrue(ConstantInventory.INVENTORY.toFile().isFile(), "the inventory exists: " + ConstantInventory.INVENTORY.toAbsolutePath().normalize());
		ConstantInventory inventory = ConstantInventory.read();
		List<String> problems = inventory.unaccounted(SOURCES, "AE/", "monitor/");
		assertEquals(new ArrayList<String>(), problems, "numbers of the audio engine the inventory and section 6 do not account for");
	}

	@Test
	public void theScanSeesConstantsDefaultsAndInlineNumbers() {
		assertEquals(Double.valueOf(60_000_000.0), ConstantInventory.evaluate("60L * 1_000_000L"));
		assertEquals(null, ConstantInventory.evaluate("12 * HOP_NANOS"));
		assertEquals(-70.0, ConstantInventory.signed("f(-70.0)", 3, 70.0));
		assertEquals(3.0, ConstantInventory.signed("x - 3.0", 4, 3.0));
		assertEquals(255.0, ConstantInventory.value("0xff"));
		assertEquals(1e-15, ConstantInventory.value("1e-15"));
		String source = "a = \"1\" + '2'; /* 3 */ // 4\nb = 5;";
		String stripped = ConstantInventory.stripped(source);
		assertEquals("5", stripped.replaceAll("[^0-9]", ""), "strings, characters and comments are not read");
		assertEquals(source.length(), stripped.length(), "positions are kept");
		assertEquals(source.indexOf('\n'), stripped.indexOf('\n'), "lines are kept");
		assertTrue(ConstantInventory.sameInUnit(120_000_000L, 120.0), "milliseconds as nanoseconds");
	}
}
