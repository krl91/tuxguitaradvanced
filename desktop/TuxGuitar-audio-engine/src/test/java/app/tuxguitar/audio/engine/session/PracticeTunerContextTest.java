package app.tuxguitar.audio.engine.session;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import app.tuxguitar.song.managers.TGSongManager;
import app.tuxguitar.song.models.TGSong;
import app.tuxguitar.song.models.TGTrack;
import app.tuxguitar.util.TGContext;

public class PracticeTunerContextTest {

	@Test
	public void oneSharedInstancePerTGContext() {
		TGContext context = new TGContext();
		assertSame(PracticeTunerContext.getInstance(context), PracticeTunerContext.getInstance(context));
	}

	@Test
	public void confirmationsArePerSongDocumentAndTrack() {
		PracticeTunerContext shared = new PracticeTunerContext();
		TGSongManager manager = new TGSongManager();
		TGSong song = manager.newSong();
		TGTrack guitar = song.getTrack(0);
		TGTrack other = manager.addTrack(song);
		TGSong reopened = manager.newSong();

		shared.confirm(song, guitar);

		assertTrue(shared.isConfirmed(song, guitar));
		assertFalse(shared.isConfirmed(song, other));
		assertFalse(shared.isConfirmed(reopened, reopened.getTrack(0)));
	}

	@Test
	public void practiceTrackTunerStateAndChosenTargetsNotifyListenersOnChange() {
		PracticeTunerContext shared = new PracticeTunerContext();
		AtomicInteger changes = new AtomicInteger();
		shared.addListener(context -> changes.incrementAndGet());
		TGTrack track = new TGSongManager().newSong().getTrack(0);

		shared.setPracticeTrack(track);
		shared.setPracticeTrack(track);
		shared.setTunerOpen(true);
		shared.setTunerOpen(true);
		shared.setTunerChosenTargets(new int[] { 64, 59, 55, 50, 45, 38 });

		assertEquals(3, changes.get());
		assertSame(track, shared.getPracticeTrack());
		assertTrue(shared.isTunerOpen());
		assertArrayEquals(new int[] { 64, 59, 55, 50, 45, 38 }, shared.getTunerChosenTargets());

		shared.setPracticeTrack(null);
		shared.setTunerOpen(false);
		shared.setTunerChosenTargets(null);
		assertNull(shared.getPracticeTrack());
		assertFalse(shared.isTunerOpen());
		assertNull(shared.getTunerChosenTargets());
	}
}
