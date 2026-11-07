package app.tuxguitar.app.tools.custom.practice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.settings.ConstantInventory;

/**
 * Every number of the practice session and of the Arcade core is accounted for (spec 026, T030, FR-002, FR-008, research R9), by the same rule
 * and the same matching as the audio engine's {@code EngineConstantInventoryTest}: a parameter's default (a parameter row of
 * {@code inventory.md}), a fixed constant of section 6 of {@code docs/architecture/recognition-engines.md}, or an inline number that is
 * mathematical or listed in the inventory.
 */
public class SessionConstantInventoryTest {

	private static final Path PRACTICE = Paths.get("src/app/tuxguitar/app/tools/custom/practice");

	@Test
	public void everySessionNumberIsAParameterOrAFixedConstant() throws Exception {
		assertTrue(ConstantInventory.INVENTORY.toFile().isFile(), "the inventory exists: " + ConstantInventory.INVENTORY.toAbsolutePath().normalize());
		ConstantInventory inventory = ConstantInventory.read();
		List<String> problems = new ArrayList<String>();
		problems.addAll(inventory.unaccounted(PRACTICE.resolve("session"), "PR/session/"));
		problems.addAll(inventory.unaccounted(PRACTICE.resolve("arcade/core"), "PR/arcade/core/"));
		assertEquals(new ArrayList<String>(), problems, "numbers of the practice session the inventory and section 6 do not account for");
	}
}
