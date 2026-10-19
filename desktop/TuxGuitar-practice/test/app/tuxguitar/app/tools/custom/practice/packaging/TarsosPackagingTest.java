package app.tuxguitar.app.tools.custom.practice.packaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Packaging guard (found on the first published packages of 0.6.0-alpha.1, 2026-10-20): the Linux and Windows packages had the audio engine but not
 * TarsosDSP, which the audio engine loads to estimate the pitch, so the recognition could not start there. Every build script that copies
 * {@code tuxguitar-audio-engine.jar} into the package must also copy {@code tarsos-dsp-core.jar} (the macOS script always did). Source check of the
 * build scripts: the packages themselves are only built by the release workflow.
 */
public class TarsosPackagingTest {

	private static List<Path> buildScriptsThatPackageTheAudioEngine() throws IOException {
		Path scripts = Paths.get(System.getProperty("user.dir"), "../build-scripts");
		List<Path> poms;
		try (Stream<Path> walk = Files.list(scripts)) {
			poms = walk.map(directory -> directory.resolve("pom.xml")).filter(Files::isRegularFile).sorted().collect(Collectors.toList());
		}
		List<Path> packaging = new ArrayList<Path>();
		for (Path pom : poms) {
			if (new String(Files.readAllBytes(pom), StandardCharsets.UTF_8).contains("<destFileName>tuxguitar-audio-engine.jar</destFileName>")) {
				packaging.add(pom);
			}
		}
		return packaging;
	}

	@Test
	public void everyBuildScriptThatPackagesTheAudioEngineAlsoPackagesTarsosDsp() throws IOException {
		List<Path> packaging = buildScriptsThatPackageTheAudioEngine();
		assertTrue(packaging.size() >= 3, "the macOS, Linux and Windows SWT scripts package the audio engine: " + packaging);
		List<String> missing = new ArrayList<String>();
		for (Path pom : packaging) {
			String text = new String(Files.readAllBytes(pom), StandardCharsets.UTF_8);
			if (!text.contains("<destFileName>tarsos-dsp-core.jar</destFileName>")) {
				missing.add(pom.getParent().getFileName().toString());
			}
		}
		assertEquals(new ArrayList<String>(), missing, "these packages would have the audio engine without TarsosDSP");
	}

	@Test
	public void theTarsosJarGoesToTheSameFolderAsTheAudioEngineJar() throws IOException {
		for (Path pom : buildScriptsThatPackageTheAudioEngine()) {
			String text = new String(Files.readAllBytes(pom), StandardCharsets.UTF_8);
			assertEquals(folderOf(text, "tuxguitar-audio-engine.jar"), folderOf(text, "tarsos-dsp-core.jar"), pom.getParent().getFileName().toString());
		}
	}

	/** The outputDirectory that follows the destFileName in its artifactItem. */
	private static String folderOf(String pom, String destFileName) {
		int at = pom.indexOf("<destFileName>" + destFileName + "</destFileName>");
		if (at < 0) {
			return "(not packaged)";
		}
		int from = pom.indexOf("<outputDirectory>", at);
		return pom.substring(from, pom.indexOf("</outputDirectory>", from));
	}
}
