package app.tuxguitar.app.tools.custom.practice.calibration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.model.PracticeEvent;
import app.tuxguitar.app.tools.custom.practice.model.PracticeSequence;
import app.tuxguitar.app.tools.custom.practice.model.PracticeSequenceBuilder;
import app.tuxguitar.app.tools.custom.practice.model.TrackTuning;
import app.tuxguitar.io.base.TGSongReaderHandle;
import app.tuxguitar.io.tg.TGSongReaderImpl;
import app.tuxguitar.song.factory.TGFactory;

/**
 * The calibration piece of spec 013 (FR-016): the owner's score {@code basic_test.tg} is kept in git in two places that must stay identical,
 * {@code tests/basic_test.tg} (the owner's file) and the copy shipped with the application as the classpath resource
 * {@code /calibration/guitar.tg}; the piece holds 13 events (10 single notes and 3 chords of 5 notes).
 */
public class CalibrationPieceTest {

	private static final String RESOURCE = "/calibration/guitar.tg";

	private static Path ownersFile() {
		return Paths.get(System.getProperty("user.dir")).resolve("../../tests/basic_test.tg").normalize();
	}

	private static byte[] resourceBytes() throws IOException {
		try (InputStream stream = CalibrationPieceTest.class.getResourceAsStream(RESOURCE)) {
			assertNotNull(stream, "the calibration piece is shipped with the plugin as the resource " + RESOURCE);
			return stream.readAllBytes();
		}
	}

	@Test
	public void theShippedPieceIsByteIdenticalToTheOwnersFile() throws IOException {
		Path file = ownersFile();
		assertTrue(Files.exists(file), "the owner's score is in the repository: " + file);
		assertArrayEquals(Files.readAllBytes(file), resourceBytes(), "the shipped copy and tests/basic_test.tg are the same file");
	}

	@Test
	public void thePieceHoldsThirteenEventsTenNotesAndThreeChordsOfFiveNotes() throws IOException {
		TGSongReaderHandle read = new TGSongReaderHandle();
		read.setFactory(new TGFactory());
		read.setInputStream(new java.io.ByteArrayInputStream(resourceBytes()));
		new TGSongReaderImpl().read(read);
		app.tuxguitar.song.models.TGTrack track = read.getSong().getTrack(0);
		PracticeSequence sequence = PracticeSequenceBuilder.build(read.getSong(), track, TrackTuning.ofAnyStrings(track));
		int notes = 0;
		int chords = 0;
		int other = 0;
		for (PracticeEvent event : sequence.getEvents()) {
			if (event.getKind() == PracticeEvent.Kind.EXPECTED_NOTE) {
				notes++;
			} else if (event.getKind() == PracticeEvent.Kind.EXPECTED_CHORD) {
				chords++;
			} else {
				other++;
			}
		}
		assertEquals(13, sequence.getEvents().size(), "events of the piece");
		assertEquals(10, notes, "single notes");
		assertEquals(3, chords, "chords");
		assertEquals(0, other, "no other kind of event");
	}
}
