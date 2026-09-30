package app.tuxguitar.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class TestUserDirectoryMigration {

	@Test
	public void copiesTheLegacyFolderWhenTheNewOneIsMissing(@TempDir Path root) throws Exception {
		Path legacy = Files.createDirectories(root.resolve("tuxguitar").resolve("config"));
		Files.write(legacy.resolve("tuxguitar.cfg"), "a=1".getBytes(StandardCharsets.UTF_8));
		Files.write(root.resolve("tuxguitar").resolve("top.txt"), "top".getBytes(StandardCharsets.UTF_8));
		File target = root.resolve("tuxguitaradvanced").toFile();

		TGUserDirectoryMigration.copyIfMissing(root.resolve("tuxguitar").toFile(), target);

		assertEquals("a=1", new String(Files.readAllBytes(target.toPath().resolve("config").resolve("tuxguitar.cfg")), StandardCharsets.UTF_8));
		assertEquals("top", new String(Files.readAllBytes(target.toPath().resolve("top.txt")), StandardCharsets.UTF_8));
		// copy, never move: upstream TuxGuitar keeps its own settings
		assertTrue(root.resolve("tuxguitar").resolve("config").resolve("tuxguitar.cfg").toFile().exists());
	}

	@Test
	public void neverTouchesAnExistingNewFolder(@TempDir Path root) throws Exception {
		Path legacy = Files.createDirectories(root.resolve("tuxguitar"));
		Files.write(legacy.resolve("old.txt"), "old".getBytes(StandardCharsets.UTF_8));
		Path target = Files.createDirectories(root.resolve("tuxguitaradvanced"));

		TGUserDirectoryMigration.copyIfMissing(legacy.toFile(), target.toFile());

		assertFalse(target.resolve("old.txt").toFile().exists());
	}

	@Test
	public void doesNothingWithoutALegacyFolder(@TempDir Path root) {
		File target = root.resolve("tuxguitaradvanced").toFile();

		TGUserDirectoryMigration.copyIfMissing(root.resolve("tuxguitar").toFile(), target);

		assertFalse(target.exists());
	}
}
