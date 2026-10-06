package app.tuxguitar.midi.synth.impl;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sound.midi.Instrument;
import javax.sound.midi.MidiChannel;
import javax.sound.midi.Patch;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import app.tuxguitar.util.TGContext;

import media.sound.EmergencySoundbank;
import media.sound.ModelChannelMixer;
import media.sound.ModelDirector;
import media.sound.ModelInstrument;
import media.sound.ModelPerformer;

/**
 * The soundbank of an instrument is built on another thread and answers later: after the
 * processor was closed, or after a newer program was asked, the answer is stale and must be
 * ignored (an upstream race: the answer used to find {@code synth == null}). The factory is
 * replaced by one that hands the callbacks to the test, so the order is fixed by the test.
 */
public class GervillProcessorLifecycleTest {

	private static Instrument first;
	private static Instrument second;

	@BeforeAll
	public static void instruments() throws Exception {
		Instrument[] all = EmergencySoundbank.createSoundbank().getInstruments();
		first = all[0];
		second = all[1];
	}

	private static final class ManualFactory extends GervillSoundbankFactory {
		private final List<GervillSoundbankCallback> callbacks = new ArrayList<GervillSoundbankCallback>();
		/** The program as the factory received it, copied at once: a later change must not show. */
		private final List<GervillProgram> received = new ArrayList<GervillProgram>();
		private final List<GervillProgram> receivedObjects = new ArrayList<GervillProgram>();

		@Override
		public void create(TGContext context, GervillProgram program, GervillSoundbankCallback callback) {
			GervillProgram copy = new GervillProgram();
			copy.copyFrom(program);
			this.received.add(copy);
			this.receivedObjects.add(program);
			this.callbacks.add(callback);
		}
	}

	/** An instrument that stops when the processor asks its patch, which it does while it holds its life cycle lock. */
	private static final class BlockingInstrument extends ModelInstrument {
		private final ModelInstrument real;
		private final CountDownLatch inside = new CountDownLatch(1);
		private final CountDownLatch release = new CountDownLatch(1);

		private BlockingInstrument(ModelInstrument real) {
			super(real.getSoundbank(), real.getPatch(), real.getName(), real.getDataClass());
			this.real = real;
		}

		@Override
		public Patch getPatch() {
			// the synthesizer asks too, under its own monitor: only the processor's call is held, or
			// a close() would be stopped by the synthesizer and not by the processor's lock
			if( !GervillProcessor.class.getName().equals(new Throwable().getStackTrace()[1].getClassName()) ) {
				return this.real.getPatch();
			}
			this.inside.countDown();
			try {
				if( !this.release.await(10, TimeUnit.SECONDS) ) {
					throw new IllegalStateException("not released");
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			return this.real.getPatch();
		}

		@Override
		public Object getData() {
			return this.real.getData();
		}

		@Override
		public ModelPerformer[] getPerformers() {
			return this.real.getPerformers();
		}

		@Override
		public ModelDirector getDirector(ModelPerformer[] performers, MidiChannel channel, media.sound.ModelDirectedPlayer player) {
			return this.real.getDirector(performers, channel, player);
		}

		@Override
		public ModelChannelMixer getChannelMixer(MidiChannel channel, javax.sound.sampled.AudioFormat format) {
			return this.real.getChannelMixer(channel, format);
		}
	}

	/** Waits, without sleeping, until the thread is stuck on a monitor. */
	private static void awaitBlocked(Thread thread) throws InterruptedException {
		long limit = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		while( thread.getState() != Thread.State.BLOCKED ) {
			assertTrue(System.nanoTime() < limit, "the thread never waited for the life cycle lock: " + thread.getState());
			Thread.yield();
		}
	}

	private static GervillProgram program(int bank, int number) {
		GervillProgram program = new GervillProgram();
		program.setBank(bank);
		program.setProgram(number);
		return program;
	}

	@Test
	public void theAnswerOfTheCurrentLoadIsLoaded() {
		ManualFactory factory = new ManualFactory();
		GervillProcessor processor = new GervillProcessor(new TGContext(), factory);
		try {
			processor.loadProgram(program(0, 25));
			assertEquals(1, factory.callbacks.size());
			assertEquals(0, processor.loadedInstruments().length);

			factory.callbacks.get(0).onCreate(first);

			assertArrayEquals(new Instrument[] { first }, processor.loadedInstruments());
			assertTrue(processor.isOpen());
		} finally {
			processor.close();
		}
	}

	@Test
	public void aDelayedAnswerAfterCloseIsIgnored() {
		ManualFactory factory = new ManualFactory();
		GervillProcessor processor = new GervillProcessor(new TGContext(), factory);
		processor.loadProgram(program(0, 25));
		processor.close();

		factory.callbacks.get(0).onCreate(first);

		assertEquals(0, processor.loadedInstruments().length);
	}

	@Test
	public void aClosedProcessorStaysClosedWhateverAnswersLate() {
		ManualFactory factory = new ManualFactory();
		GervillProcessor processor = new GervillProcessor(new TGContext(), factory);
		processor.loadProgram(program(0, 25));
		processor.close();
		assertFalse(processor.isOpen());

		factory.callbacks.get(0).onCreate(first);
		processor.loadProgram(program(0, 30));
		processor.loadInstrument();
		processor.loadInstrument(second);

		assertFalse(processor.isOpen());
		assertEquals(1, factory.callbacks.size(), "a closed processor must not start another load");
		assertEquals(0, processor.loadedInstruments().length);
	}

	@Test
	public void anOlderAnswerDoesNotReplaceTheInstrumentOfTheNewerProgram() {
		ManualFactory factory = new ManualFactory();
		GervillProcessor processor = new GervillProcessor(new TGContext(), factory);
		try {
			GervillProgram asked = program(0, 25);
			processor.loadProgram(asked);
			processor.loadProgram(program(0, 30));
			assertEquals(2, factory.callbacks.size());

			// the first request is still bank/program A after the second one, the second is B
			assertEquals(0, factory.received.get(0).getBank());
			assertEquals(25, factory.received.get(0).getProgram());
			assertEquals(0, factory.received.get(1).getBank());
			assertEquals(30, factory.received.get(1).getProgram());
			// the factory never gets the processor's own mutable program, nor the caller's object
			assertNotSame(processor.getProgram(), factory.receivedObjects.get(0));
			assertNotSame(processor.getProgram(), factory.receivedObjects.get(1));
			assertNotSame(asked, factory.receivedObjects.get(0));
			// the processor's program moved on to B, the request already made did not
			assertEquals(30, processor.getProgram().getProgram());
			assertEquals(25, factory.receivedObjects.get(0).getProgram());
			// changing the object given to loadProgram() later does not change the request
			asked.setProgram(99);
			asked.setBank(7);
			assertEquals(25, factory.receivedObjects.get(0).getProgram());
			assertEquals(0, factory.receivedObjects.get(0).getBank());

			factory.callbacks.get(1).onCreate(second);
			factory.callbacks.get(0).onCreate(first);

			Instrument[] loaded = processor.loadedInstruments();
			assertEquals(1, loaded.length);
			assertSame(second, loaded[0]);
		} finally {
			processor.close();
		}
	}

	@Test
	public void anOlderAnswerArrivingFirstIsAlsoIgnored() {
		ManualFactory factory = new ManualFactory();
		GervillProcessor processor = new GervillProcessor(new TGContext(), factory);
		try {
			processor.loadProgram(program(0, 25));
			processor.loadProgram(program(0, 30));

			factory.callbacks.get(0).onCreate(first);
			assertEquals(0, processor.loadedInstruments().length);

			factory.callbacks.get(1).onCreate(second);
			assertArrayEquals(new Instrument[] { second }, processor.loadedInstruments());
		} finally {
			processor.close();
		}
	}

	@Test
	public void manyQuickOpeningsAndClosingsNeverFail() {
		for( int i = 0 ; i < 40 ; i ++ ) {
			ManualFactory factory = new ManualFactory();
			GervillProcessor processor = new GervillProcessor(new TGContext(), factory);
			processor.loadProgram(program(0, 10 + i));
			processor.loadProgram(program(0, 60 + i));
			if( i % 2 == 0 ) {
				factory.callbacks.get(1).onCreate(second);
			}
			processor.close();
			// all the answers come back late, in both orders
			factory.callbacks.get(0).onCreate(first);
			factory.callbacks.get(1).onCreate(second);
			factory.callbacks.get(0).onCreate(first);

			assertFalse(processor.isOpen());
			assertEquals(0, processor.loadedInstruments().length);
		}
	}

	@Test
	public void aCloseAskedWhileAnAnswerIsBeingLoadedWaitsForIt() throws Exception {
		ManualFactory factory = new ManualFactory();
		GervillProcessor processor = new GervillProcessor(new TGContext(), factory);
		BlockingInstrument held = new BlockingInstrument((ModelInstrument) first);
		processor.loadProgram(program(0, 25));
		GervillSoundbankCallback answer = factory.callbacks.get(0);

		ExecutorService threads = Executors.newFixedThreadPool(2);
		try {
			// thread 1 is inside the processor, with its life cycle lock, in the middle of loading
			Future<?> answering = threads.submit(() -> answer.onCreate(held));
			assertTrue(held.inside.await(10, TimeUnit.SECONDS));

			final Thread[] closer = new Thread[1];
			final CountDownLatch closerStarted = new CountDownLatch(1);
			Future<?> closing = threads.submit(() -> {
				closer[0] = Thread.currentThread();
				closerStarted.countDown();
				processor.close();
			});
			assertTrue(closerStarted.await(10, TimeUnit.SECONDS));
			awaitBlocked(closer[0]);
			assertFalse(closing.isDone(), "close() went through while an answer was being loaded");

			held.release.countDown();
			assertNull(answering.get(10, TimeUnit.SECONDS), "the answer failed (a null synth, most likely)");
			assertNull(closing.get(10, TimeUnit.SECONDS));

			assertFalse(processor.isOpen());
			assertEquals(0, processor.loadedInstruments().length);
		} finally {
			held.release.countDown();
			threads.shutdownNow();
			processor.close();
		}
	}

	@Test
	public void anOlderAnswerWaitingBehindANewerOneIsIgnored() throws Exception {
		ManualFactory factory = new ManualFactory();
		GervillProcessor processor = new GervillProcessor(new TGContext(), factory);
		BlockingInstrument newer = new BlockingInstrument((ModelInstrument) second);
		processor.loadProgram(program(0, 25));
		processor.loadProgram(program(0, 30));
		GervillSoundbankCallback older = factory.callbacks.get(0);
		GervillSoundbankCallback current = factory.callbacks.get(1);

		ExecutorService threads = Executors.newFixedThreadPool(2);
		try {
			Future<?> answeringCurrent = threads.submit(() -> current.onCreate(newer));
			assertTrue(newer.inside.await(10, TimeUnit.SECONDS));

			final Thread[] late = new Thread[1];
			final CountDownLatch lateStarted = new CountDownLatch(1);
			Future<?> answeringOlder = threads.submit(() -> {
				late[0] = Thread.currentThread();
				lateStarted.countDown();
				older.onCreate(first);
			});
			assertTrue(lateStarted.await(10, TimeUnit.SECONDS));
			awaitBlocked(late[0]);

			newer.release.countDown();
			assertNull(answeringCurrent.get(10, TimeUnit.SECONDS));
			assertNull(answeringOlder.get(10, TimeUnit.SECONDS));

			Instrument[] loaded = processor.loadedInstruments();
			assertEquals(1, loaded.length, "the older answer loaded its instrument too");
			assertSame(newer, loaded[0]);
			assertTrue(processor.isOpen());
		} finally {
			newer.release.countDown();
			threads.shutdownNow();
			processor.close();
		}
	}

	@Test
	public void aNewProgramWaitsForTheAnswerBeingLoadedBeforeItIsCopied() throws Exception {
		ManualFactory factory = new ManualFactory();
		GervillProcessor processor = new GervillProcessor(new TGContext(), factory);
		BlockingInstrument held = new BlockingInstrument((ModelInstrument) first);
		processor.loadProgram(program(0, 25));
		GervillSoundbankCallback answer = factory.callbacks.get(0);

		ExecutorService threads = Executors.newFixedThreadPool(2);
		try {
			Future<?> answering = threads.submit(() -> answer.onCreate(held));
			assertTrue(held.inside.await(10, TimeUnit.SECONDS));

			final Thread[] changer = new Thread[1];
			final CountDownLatch started = new CountDownLatch(1);
			Future<?> changing = threads.submit(() -> {
				changer[0] = Thread.currentThread();
				started.countDown();
				processor.loadProgram(program(0, 30));
			});
			assertTrue(started.await(10, TimeUnit.SECONDS));
			awaitBlocked(changer[0]);
			assertEquals(25, processor.getProgram().getProgram(), "the program was changed while an answer was being loaded");
			assertEquals(1, factory.callbacks.size());

			held.release.countDown();
			assertNull(answering.get(10, TimeUnit.SECONDS));
			assertNull(changing.get(10, TimeUnit.SECONDS));

			assertEquals(30, processor.getProgram().getProgram());
			assertEquals(2, factory.callbacks.size());
			assertEquals(30, factory.received.get(1).getProgram());
			assertEquals(25, factory.received.get(0).getProgram());
		} finally {
			held.release.countDown();
			threads.shutdownNow();
			processor.close();
		}
	}
}
