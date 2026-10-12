package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import app.tuxguitar.player.base.MidiSequenceHandler;
import app.tuxguitar.player.base.MidiTransmitter;
import app.tuxguitar.player.impl.sequencer.MidiSequencerImpl;
import app.tuxguitar.thread.TGMultiThreadHandler;
import app.tuxguitar.thread.TGThreadManager;
import app.tuxguitar.util.TGContext;

/** The tempo clock of the player (spec 002 T093): scripted sources, then the real sequencer of the player with a dummy output. */
public class PlayerTempoClockTest {

	@Test
	public void readsWhatTheSourcesSayAndTheWallClockItIsGiven() {
		final AtomicLong wall = new AtomicLong(1_000_000_000L);
		final AtomicLong tick = new AtomicLong(0);
		PlayerTempoClock clock = new PlayerTempoClock(wall::get, tick::get, 1920.0);

		assertEquals(1_000_000_000L, clock.nowNanos());
		assertTrue(Double.isNaN(clock.tickAt(wall.get())));
		// a step of 19 ticks every 10 ms (1900 ticks a second, a little under 120 bpm), polled every 5 ms
		for( int i = 0 ; i < 200 ; i ++ ) {
			wall.addAndGet(5_000_000L);
			tick.set((i + 1) / 2 * 19L);
			clock.poll();
		}
		double now = clock.tickAt(wall.get());
		assertEquals(tick.get(), now, 25.0, "the position is the one the source gave, within a step");
		assertEquals(1900.0, clock.ticksPerSecond(), 10.0, "the rate of the source was not learned");
		assertEquals(wall.get(), clock.wallNanosAt(now), 2_000_000L);
	}

	@Test
	public void aSourceThatSaysNothingIsIgnored() {
		final AtomicLong wall = new AtomicLong(0);
		PlayerTempoClock clock = new PlayerTempoClock(wall::get, () -> -1L, 1920.0);
		for( int i = 0 ; i < 50 ; i ++ ) {
			wall.addAndGet(5_000_000L);
			clock.poll();
		}
		assertTrue(Double.isNaN(clock.tickAt(wall.get())), "a position that does not exist was taken for a position");
		assertFalse(clock.isStalled(wall.get()) && !Double.isNaN(clock.tickAt(wall.get())));
	}

	@Test
	public void followsTheRealSequencerOfThePlayer() throws Exception {
		TGContext context = new TGContext();
		TGThreadManager.getInstance(context).setThreadHandler(new TGMultiThreadHandler());
		final MidiSequencerImpl sequencer = new MidiSequencerImpl(context);
		MidiSequenceHandler handler = sequencer.createSequence(2);
		handler.addTempoInUSQ(0, 0, 500_000);
		handler.addNoteOn(960L * 4000, 1, 0, 60, 64, 0, false);
		handler.notifyFinish();
		sequencer.setTransmitter(new MidiTransmitter() {
			public void sendNoteOn(int c, int k, int v, int vo, boolean b) {}
			public void sendNoteOff(int c, int k, int v, int vo, boolean b) {}
			public void sendPitchBend(int c, int v, int vo, boolean b) {}
			public void sendProgramChange(int c, int v) {}
			public void sendControlChange(int c, int con, int v) {}
			public void sendSystemReset() {}
			public void sendAllNotesOff() {}
			public void sendPitchBendReset() {}
			public void sendParameter(int c, String k, String v) {}
		});
		sequencer.setTickPosition(0);
		PlayerTempoClock clock = new PlayerTempoClock(System::nanoTime, () -> {
			try {
				return sequencer.getTickPosition();
			} catch (Exception exception) {
				return -1L;
			}
		}, 1920.0);
		sequencer.start();
		double worstMs = 0.0;
		int measured = 0;
		try {
			long end = System.nanoTime() + 2_500_000_000L;
			long started = System.nanoTime();
			while( System.nanoTime() < end ) {
				clock.poll();
				if( System.nanoTime() - started > 1_000_000_000L && clock.tickAt(System.nanoTime()) == clock.tickAt(System.nanoTime()) ) {
					long now = System.nanoTime();
					double interpolated = clock.tickAt(now);
					long raw = sequencer.getTickPosition();
					// a measure whose two readings are far apart in time was cut by the scheduler of a loaded machine (a shared CI runner gave 57 ms
					// once): it says nothing about the clock, so it is left out; what is kept is judged as strictly as before
					if( System.nanoTime() - now <= 3_000_000L ) {
						measured++;
						// the interpolated position must stay within one step of the sequencer's own (steps of 10 to 18 ms)
						worstMs = Math.max(worstMs, Math.abs(interpolated - raw) / clock.ticksPerSecond() * 1000.0);
					}
				}
				Thread.sleep(1);
			}
		} finally {
			sequencer.stop();
		}
		assertEquals(1920.0, clock.ticksPerSecond(), 1920.0 * 0.02, "the rate of the real sequencer was not learned");
		assertTrue(measured >= 30, "too few undisturbed measures to judge the clock: " + measured);
		assertTrue(worstMs < 40.0, "the interpolated position left the sequencer's by " + worstMs + " ms");
	}
}
