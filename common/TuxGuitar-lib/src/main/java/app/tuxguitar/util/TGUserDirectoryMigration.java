package app.tuxguitar.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;

/** Takes over the user data of the former folder name (see {@link TGUserFileUtils}). */
final class TGUserDirectoryMigration {

	private TGUserDirectoryMigration() {
	}

	/**
	 * Copies {@code legacy} to {@code target} when {@code target} does not exist yet. A copy, never a
	 * move, so a separately installed upstream TuxGuitar keeps its own settings. Best effort: a
	 * failure leaves what was copied and never stops the application.
	 */
	static void copyIfMissing(File legacy, File target) {
		try {
			if( target.exists() || !legacy.isDirectory() ) {
				return;
			}
			final Path source = legacy.toPath();
			final Path destination = target.toPath();
			Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
					Files.createDirectories(destination.resolve(source.relativize(dir).toString()));
					return FileVisitResult.CONTINUE;
				}
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					Files.copy(file, destination.resolve(source.relativize(file).toString()), StandardCopyOption.COPY_ATTRIBUTES);
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (Throwable throwable) {
			throwable.printStackTrace();
		}
	}
}
