package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.model.GuitarConfirmationMemory;
import app.tuxguitar.app.tools.custom.practice.model.PracticeTrackCandidates;
import app.tuxguitar.app.tools.custom.practice.model.PracticeTrackSelection;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.session.PracticeSession.State;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.audio.engine.pitch.NoteInterpreter;
import app.tuxguitar.audio.engine.pitch.TolerancePolicy;
import app.tuxguitar.editor.event.TGUpdateEvent;
import app.tuxguitar.player.base.MidiPlayerEvent;
import app.tuxguitar.thread.TGMultiThreadHandler;
import app.tuxguitar.thread.TGThreadManager;
import app.tuxguitar.util.TGContext;

public class SessionEventsTest {

	private TGContext context;
	private BlockingQueue<State> states;
	private SessionLoop<PracticeSnapshot> loop;
	private SessionEvents events;

	@BeforeEach
	public void setUp() throws InterruptedException {
		this.context = new TGContext();
		TGThreadManager.getInstance(this.context).setThreadHandler(new TGMultiThreadHandler());
		SongFixture fixture = new SongFixture(1);
		fixture.quarter(0, 0, 1, 0);
		PracticeTrackSelection selection = new PracticeTrackSelection(fixture.song,
				PracticeTrackCandidates.evaluate(fixture.song, fixture.track()).getPreselected(),
				GuitarConfirmationMemory.getInstance(this.context));
		selection.accept();
		PracticeSession session = new PracticeSession(selection, fixture.sequence(), new NoteInterpreter(0.8),
				new TolerancePolicy(5.0, 30.0), 100_000_000L, 0L);
		session.setAssessment(new InputAssessment(InputAssessment.Status.SUITABLE, InputAssessment.Reason.NONE));
		this.states = new LinkedBlockingQueue<State>();
		this.loop = new SessionLoop<PracticeSnapshot>(this.context, session, snapshot -> this.states.add(snapshot.getState()), Runnable::run);
		this.events = new SessionEvents(this.context, this.loop, session);
		this.loop.start();
		this.loop.postCommand(PracticeCommand.START);
		assertEquals(State.RUNNING, next());
	}

	@AfterEach
	public void tearDown() {
		this.loop.stop();
		TGThreadManager.getInstance(this.context).dispose();
	}

	@Test
	public void playbackPausesUntilAnExplicitResume() throws InterruptedException {
		this.events.processEvent(new MidiPlayerEvent(MidiPlayerEvent.NOTIFY_STARTED));
		assertEquals(State.PAUSED, next());

		this.events.processEvent(new MidiPlayerEvent(MidiPlayerEvent.NOTIFY_STOPPED));
		assertEquals(State.READY_TO_RESUME, next());

		this.loop.postCommand(PracticeCommand.RESUME);
		assertEquals(State.RUNNING, next());
	}

	@Test
	public void selectionChangesAreIgnoredAndSongChangesStop() throws InterruptedException {
		this.events.processEvent(new TGUpdateEvent(TGUpdateEvent.SELECTION, null));
		this.loop.postCommand(PracticeCommand.RESUME);
		assertEquals(State.RUNNING, next());

		this.events.processEvent(new TGUpdateEvent(TGUpdateEvent.MEASURE_UPDATED, null));
		assertEquals(State.STOPPED, next());
	}

	private State next() throws InterruptedException {
		State state = this.states.poll(2, TimeUnit.SECONDS);
		assertTrue(state != null, "no session snapshot delivered");
		return state;
	}
}
