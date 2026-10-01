package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.model.PracticeTrackCandidates;
import app.tuxguitar.app.tools.custom.practice.model.PracticeTrackSelection;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.session.PracticeSession.State;
import app.tuxguitar.audio.engine.input.AudioInputDescriptor;
import app.tuxguitar.audio.engine.input.CurrentInputSelection;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.audio.engine.pitch.NoteInterpreter;
import app.tuxguitar.audio.engine.pitch.TolerancePolicy;
import app.tuxguitar.editor.event.TGUpdateEvent;
import app.tuxguitar.player.base.MidiPlayerEvent;
import app.tuxguitar.thread.TGMultiThreadHandler;
import app.tuxguitar.thread.TGThreadManager;
import app.tuxguitar.util.TGContext;
import app.tuxguitar.audio.engine.session.PracticeTunerContext;

public class SessionEventsTest {

	private TGContext context;
	private BlockingQueue<State> states;
	private SessionLoop<PracticeSnapshot> loop;
	private SessionEvents events;
	private CurrentInputSelection selection;
	private PracticeTunerContext shared;

	@BeforeEach
	public void setUp() throws InterruptedException {
		this.context = new TGContext();
		TGThreadManager.getInstance(this.context).setThreadHandler(new TGMultiThreadHandler());
		SongFixture fixture = new SongFixture(1);
		fixture.quarter(0, 0, 1, 0);
		PracticeTrackSelection selection = new PracticeTrackSelection(fixture.song,
				PracticeTrackCandidates.evaluate(fixture.song, fixture.track()).getPreselected(),
				PracticeTunerContext.getInstance(this.context));
		selection.accept();
		PracticeSession session = new PracticeSession(selection, fixture.sequence(), new NoteInterpreter(0.8),
				new TolerancePolicy(5.0, 30.0), 100_000_000L, 0L);
		session.setAssessment(new InputAssessment(InputAssessment.Status.SUITABLE, InputAssessment.Reason.NONE));
		this.states = new LinkedBlockingQueue<State>();
		this.loop = new SessionLoop<PracticeSnapshot>(this.context, session, snapshot -> this.states.add(snapshot.getState()), Runnable::run);
		this.selection = CurrentInputSelection.getInstance(this.context);
		this.shared = PracticeTunerContext.getInstance(this.context);
		this.events = new SessionEvents(this.context, this.loop, session, this.selection, this.shared);
		this.shared.addListener(changed -> this.events.tunerStateChanged(changed.isTunerOpen()));
		this.selection.addListener(this.events::selectionChanged);
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
	public void practiceNeverStopsOnItsOwnBecauseOfScoreUpdateEvents() throws InterruptedException {
		this.events.processEvent(new TGUpdateEvent(TGUpdateEvent.SELECTION, null));
		this.loop.postCommand(PracticeCommand.RESUME);
		assertEquals(State.RUNNING, next());

		// the application fires these on its own (for example when the measure-errors dialog moves the
		// cursor): they used to stop practice, now the session stays running
		for( int i = 0 ; i < 5 ; i ++ ) {
			this.events.processEvent(new TGUpdateEvent(TGUpdateEvent.MEASURE_UPDATED, null));
			this.events.processEvent(new TGUpdateEvent(TGUpdateEvent.SONG_UPDATED, null));
		}
		// any later command is answered by a snapshot: it shows the state after those events
		this.loop.postCommand(PracticeCommand.RESUME);
		assertEquals(State.RUNNING, next());
	}

	@Test
	public void loadingAnotherSongStopsPracticeBecauseItsSongIsGone() throws InterruptedException {
		this.loop.postCommand(PracticeCommand.RESUME);
		assertEquals(State.RUNNING, next());

		this.events.processEvent(new TGUpdateEvent(TGUpdateEvent.SONG_LOADED, null));
		assertEquals(State.STOPPED, next());
	}

	@Test
	public void theLearnerStopsPracticeWithTheStopCommand() throws InterruptedException {
		this.loop.postCommand(PracticeCommand.RESUME);
		assertEquals(State.RUNNING, next());

		this.loop.postCommand(PracticeCommand.STOP);
		assertEquals(State.STOPPED, next());
	}

	@Test
	public void unusableInputPausesUntilACheckSucceedsAndAnExplicitResume() throws InterruptedException {
		this.selection.updateAssessment(new InputAssessment(InputAssessment.Status.UNUSABLE, InputAssessment.Reason.DEVICE_LOST));
		assertEquals(State.PAUSED, next());

		this.selection.updateAssessment(new InputAssessment(InputAssessment.Status.SUBOPTIMAL, InputAssessment.Reason.TOO_LOW));
		assertEquals(State.READY_TO_RESUME, next());

		this.loop.postCommand(PracticeCommand.RESUME);
		assertEquals(State.RUNNING, next());
	}

	@Test
	public void changingTheInputPausesUntilTheNewInputWasChecked() throws InterruptedException {
		this.selection.select(new AudioInputDescriptor("other", "Interface", 1, 2, "Interface — Input 2",
				AudioInputDescriptor.Kind.UNKNOWN, false), null);
		assertEquals(State.PAUSED, next());

		this.selection.updateAssessment(new InputAssessment(InputAssessment.Status.SUITABLE, InputAssessment.Reason.NONE));
		assertEquals(State.READY_TO_RESUME, next());
	}

	@Test
	public void outdatedSelectionNotificationsAreIgnored() throws InterruptedException {
		CurrentInputSelection.Snapshot stale = this.selection.snapshot();
		this.selection.updateAssessment(new InputAssessment(InputAssessment.Status.UNUSABLE, InputAssessment.Reason.NO_SIGNAL));
		assertEquals(State.PAUSED, next());

		this.events.selectionChanged(stale);
		this.loop.postCommand(PracticeCommand.RESUME);
		assertEquals(State.PAUSED, next());
	}

	@Test
	public void openingTheTunerPausesUntilItClosesAndAnExplicitResume() throws InterruptedException {
		this.shared.setTunerOpen(true);
		assertEquals(State.PAUSED, next());

		this.shared.setTunerOpen(false);
		assertEquals(State.READY_TO_RESUME, next());

		this.loop.postCommand(PracticeCommand.RESUME);
		assertEquals(State.RUNNING, next());
	}

	private State next() throws InterruptedException {
		State state = this.states.poll(2, TimeUnit.SECONDS);
		assertTrue(state != null, "no session snapshot delivered");
		return state;
	}
}
