package app.tuxguitar.io.gtp;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import app.tuxguitar.song.managers.TGSongManager;
import app.tuxguitar.song.models.TGSong;

/**
 * Optional smoke test on a folder of Guitar Pro files (spec 002 T146): every .gp3 and .gp4 of the directory named by the environment variable
 * TG_GP_CORPUS is read by content, its measure errors counted and its MIDI sequence generated. Skipped when the variable is not set. It reports
 * counts (read, not read, files with measure errors, files whose sequence could not be generated) and asserts nothing: the files are the
 * owner's, usually copyright material, and are never committed. TG_GP_CORPUS_LIMIT caps the number of files (the first ones, sorted by name).
 */
public class TestGPCorpusSmoke {

	@Test
	public void readEveryFileOfTheCorpus() throws IOException {
		String folder = System.getenv("TG_GP_CORPUS");
		assumeTrue(folder != null && !folder.isEmpty(), "TG_GP_CORPUS is not set: no corpus to read");
		Path root = Paths.get(folder);
		assumeTrue(Files.isDirectory(root), "TG_GP_CORPUS is not a directory: " + folder);
		List<Path> files;
		try( Stream<Path> stream = Files.walk(root) ) {
			files = stream.filter(Files::isRegularFile).filter(p -> p.toString().toLowerCase().endsWith(".gp3") || p.toString().toLowerCase().endsWith(".gp4")).sorted().collect(Collectors.toList());
		}
		String limit = System.getenv("TG_GP_CORPUS_LIMIT");
		if( limit != null && !limit.isEmpty() && Integer.parseInt(limit) < files.size() ) {
			files = files.subList(0, Integer.parseInt(limit));
		}
		int read = 0;
		int notRead = 0;
		int withErrors = 0;
		int noSequence = 0;
		List<String> failures = new ArrayList<String>();
		for( Path file : files ) {
			TGSong song;
			try {
				String name = file.getFileName().toString().toLowerCase();
				song = GtpTestSupport.read(Files.readAllBytes(file), name.substring(name.lastIndexOf('.') + 1));
				new TGSongManager().updatePreciseStart(song);
			} catch (Throwable throwable) {
				notRead ++;
				continue;
			}
			read ++;
			if( !new TGSongManager().getMeasureErrors(song).isEmpty() ) {
				withErrors ++;
			}
			try {
				GtpTestSupport.midiNotes(song);
			} catch (Throwable throwable) {
				noSequence ++;
				if( failures.size() < 10 ) {
					failures.add(file.getFileName() + ": " + throwable);
				}
			}
		}
		System.out.println("Guitar Pro corpus " + root + ": " + files.size() + " files, " + read + " read, " + notRead + " not read, " + withErrors
				+ " with measure errors, " + noSequence + " whose MIDI sequence could not be generated" + (failures.isEmpty() ? "" : " " + failures));
	}
}
