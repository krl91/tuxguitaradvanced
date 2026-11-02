package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

/** Spec 022 (T042, FR-019, data-model "ScoreKey"): a score is known by its title and artist, by its file name when both are empty. */
public class ScoreKeyTest {

	@Test
	public void titleAndArtistAreLowerCasedTrimmedAndHashedToSixteenHexCharacters() {
		Optional<ScoreKey> key = ScoreKey.of("  Smoke On The Water ", " Deep Purple", "smoke.gp5");
		assertTrue(key.isPresent());
		String value = key.get().value();
		assertTrue(value.matches("[0-9a-f]{16}"), value);
		assertFalse(value.contains("smoke"), "no title text in the key");
		assertEquals(key, ScoreKey.of("smoke on the water", "deep purple", "other.gp5"), "case and spaces do not matter, nor the file");
		assertEquals(ScoreKey.sha256Prefix("smoke on the water — deep purple"), value, "the title and the artist joined by ' — '");
	}

	@Test
	public void theFileNameIsUsedWhenTitleAndArtistAreEmptyAndNothingGivesNoKey() {
		Optional<ScoreKey> byFile = ScoreKey.of("", "  ", "Exercise 3.tg");
		assertTrue(byFile.isPresent());
		assertEquals(ScoreKey.sha256Prefix("exercise 3.tg"), byFile.get().value());
		assertFalse(ScoreKey.of(null, "", null).isPresent(), "a new unsaved score has no key");
		assertFalse(ScoreKey.of(" ", " ", " ").isPresent());
	}

	@Test
	public void theSameTitleAndArtistInTwoFilesGiveTheSameKeyAndMovingTheFileKeepsIt() {
		assertEquals(ScoreKey.of("Song", "Band", "/a/song.gp"), ScoreKey.of("Song", "Band", "/b/renamed.tg"));
		assertNotEquals(ScoreKey.of("Song", "Band", "x"), ScoreKey.of("Song", "Other band", "x"));
		assertNotEquals(ScoreKey.of("Song", "", "x"), ScoreKey.of("", "Song", "x"), "the title and the artist keep their places");
	}
}
