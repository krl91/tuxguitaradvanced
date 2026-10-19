package app.tuxguitar.app.tools.custom.practice.calibration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import app.tuxguitar.app.tools.custom.practice.calibration.CalibrationFlow.Event;
import app.tuxguitar.app.tools.custom.practice.calibration.CalibrationFlow.State;
import app.tuxguitar.audio.engine.analysis.HopListener;
import app.tuxguitar.audio.engine.calibration.DeviceKey;
import app.tuxguitar.audio.engine.calibration.FrontEndState;
import app.tuxguitar.audio.engine.calibration.HopRecorder;
import app.tuxguitar.audio.engine.calibration.ProfileLibrary;
import app.tuxguitar.audio.engine.calibration.RecognitionProfile;
import app.tuxguitar.audio.engine.calibration.RecognitionRate;
import app.tuxguitar.audio.engine.calibration.RecordingQuality;
import app.tuxguitar.audio.engine.calibration.SoundCounter;
import app.tuxguitar.audio.engine.calibration.SoundSegmenter;
import app.tuxguitar.audio.engine.settings.ProfileParameters;
import app.tuxguitar.audio.engine.settings.RecognitionParameter;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;

/**
 * Does what each step of the calibration needs (spec 013): listens through a port on the engine, measures and cuts the recording, runs the offline search
 * on a worker and saves the profile into the library, following {@link CalibrationFlow}. Nothing is saved before the search has ended, and then only in
 * the four cases of FR-007; the recording lives in memory and is dropped as soon as it is refused, analysed, or the calibration stops (FR-017). It knows
 * no window: it publishes a {@link Snapshot} at each change, from any thread, and the window shows it on the interface thread.
 */
public final class CalibrationController {

	/** Why a recording was refused; the numbers are in the snapshot. */
	public enum RefusalCause {
		WRONG_COUNT,
		SILENT,
		CLIPPED,
		SILENCE_TIMEOUT,
		TOTAL_TIMEOUT,
		/** the player said it is not good enough */
		NOT_GOOD_ENOUGH
	}

	/** Which result screen: saved, or one of the two messages that go with the three choices. */
	public enum ResultKind {
		SAVED,
		/** "the default settings are the most optimal" */
		NOT_BETTER,
		/** "better than the default, but only N %" */
		BETTER_UNDER_60
	}

	/** The engine side of the listening: hands the hops of the analysis to the listeners and gives the state of the input. */
	public interface ListeningPort {
		/** Makes sure the analysis runs and gives the listeners every hop until {@link #stop}. */
		void start(HopListener... listeners);

		void stop();

		FrontEndState frontEnd();

		/** The capture rate now, or 0 when it is not known. */
		int sampleRate();
	}

	public interface TunerPort {
		boolean available();

		void open();

		/** The listener runs when the tuner opened through {@link #open()} is closed; null removes it. */
		void setClosedListener(Runnable listener);
	}

	public interface ScorerFactory {
		ProfileSearch.Scoring create(List<float[]> segments, int sampleRate);
	}

	/**
	 * Writes the diagnostic copy the player asked for (FR-020) and returns where, or null when it could not be written. The controller itself uses no file
	 * API: the sink is given by the application and is the only code that writes sound.
	 */
	public interface DiagnosticSink {
		String write(float[] samples, int sampleRate);
	}

	public interface Listener {
		void changed(Snapshot snapshot);
	}

	/** Everything a window needs to show the step. */
	public static final class Snapshot {
		private final State state;
		private final boolean hasProfile;
		private final int heard;
		private final int expected;
		private final RefusalCause refusalCause;
		private final RecordingQuality quality;
		private final int progressDone;
		private final int progressBudget;
		private final ResultKind resultKind;
		private final RecognitionRate foundRate;
		private final RecognitionRate defaultRate;
		private final boolean stoppedAtLimit;
		private final boolean saveFailed;
		private final boolean tunerUnavailable;
		private final String diagnosticPath;
		private final boolean diagnosticFailed;

		Snapshot(State state, boolean hasProfile, int heard, int expected, RefusalCause refusalCause, RecordingQuality quality, int progressDone, int progressBudget,
				ResultKind resultKind, RecognitionRate foundRate, RecognitionRate defaultRate, boolean stoppedAtLimit, boolean saveFailed, boolean tunerUnavailable,
				String diagnosticPath, boolean diagnosticFailed) {
			this.state = state;
			this.hasProfile = hasProfile;
			this.heard = heard;
			this.expected = expected;
			this.refusalCause = refusalCause;
			this.quality = quality;
			this.progressDone = progressDone;
			this.progressBudget = progressBudget;
			this.resultKind = resultKind;
			this.foundRate = foundRate;
			this.defaultRate = defaultRate;
			this.stoppedAtLimit = stoppedAtLimit;
			this.saveFailed = saveFailed;
			this.tunerUnavailable = tunerUnavailable;
			this.diagnosticPath = diagnosticPath;
			this.diagnosticFailed = diagnosticFailed;
		}

		public State getState() {
			return this.state;
		}

		/** The device already has a profile: the offer says "Calibrate again" and "Use the default configuration (replaces the current calibration)". */
		public boolean hasProfile() {
			return this.hasProfile;
		}

		/** The sounds heard so far (while listening), or heard in the refused recording. */
		public int getHeard() {
			return this.heard;
		}

		public int getExpected() {
			return this.expected;
		}

		public RefusalCause getRefusalCause() {
			return this.refusalCause;
		}

		/** The measures for the quality question. */
		public RecordingQuality getQuality() {
			return this.quality;
		}

		public int getProgressDone() {
			return this.progressDone;
		}

		public int getProgressBudget() {
			return this.progressBudget;
		}

		public ResultKind getResultKind() {
			return this.resultKind;
		}

		/** The rate of the settings found (the best of the search) on the recording. */
		public RecognitionRate getFoundRate() {
			return this.foundRate;
		}

		public RecognitionRate getDefaultRate() {
			return this.defaultRate;
		}

		/** The budget ended the search before it converged. */
		public boolean isStoppedAtLimit() {
			return this.stoppedAtLimit;
		}

		/** Saving the profile failed: the previous profile is kept and the settings found are not used. */
		public boolean isSaveFailed() {
			return this.saveFailed;
		}

		/** The player asked for the tuner and it could not be opened. */
		public boolean isTunerUnavailable() {
			return this.tunerUnavailable;
		}

		/** Where the diagnostic copy of the recording was written (FR-020), or null when none was asked for or it failed. */
		public String getDiagnosticPath() {
			return this.diagnosticPath;
		}

		/** A diagnostic copy was asked for and could not be written; the calibration went on. */
		public boolean isDiagnosticFailed() {
			return this.diagnosticFailed;
		}
	}

	private final int expected;
	private final DeviceKey key;
	private final ProfileLibrary library;
	private final Supplier<RecognitionSettings> defaults;
	private final ListeningPort port;
	private final TunerPort tuner;
	private final ScorerFactory scorers;
	private final Executor searchExecutor;
	private final LongSupplier clockMillis;
	private final Listener listener;
	private final int candidateBudget;
	private final CalibrationFlow flow = new CalibrationFlow();

	private final boolean hasProfile;
	private SoundCounter counter;
	private HopRecorder recorder;
	private List<float[]> segments;
	private int recordedRate;
	private FrontEndState recordedFrontEnd;
	private RecordingQuality quality;
	private int heard;
	private RefusalCause refusalCause;
	private int progressDone;
	private int progressBudget;
	private ResultKind resultKind;
	private ProfileSearch.Result pending;
	private boolean saveFailed;
	private boolean tunerUnavailable;
	private DiagnosticSink diagnosticSink;
	/** The whole recording, kept in memory only while a sink is set and until the quality answer. */
	private float[] keptSamples;
	private String diagnosticPath;
	private boolean diagnosticFailed;
	private volatile ProfileSearch search;

	public CalibrationController(int expected, DeviceKey key, ProfileLibrary library, Supplier<RecognitionSettings> defaults, ListeningPort port, TunerPort tuner,
			ScorerFactory scorers, Executor searchExecutor, LongSupplier clockMillis, Listener listener) {
		this(expected, key, library, defaults, port, tuner, scorers, searchExecutor, clockMillis, listener, ProfileSearch.BUDGET_CANDIDATES);
	}

	public CalibrationController(int expected, DeviceKey key, ProfileLibrary library, Supplier<RecognitionSettings> defaults, ListeningPort port, TunerPort tuner,
			ScorerFactory scorers, Executor searchExecutor, LongSupplier clockMillis, Listener listener, int candidateBudget) {
		this.expected = expected;
		this.key = key;
		this.library = library;
		this.defaults = defaults;
		this.port = port;
		this.tuner = tuner;
		this.scorers = scorers;
		this.searchExecutor = searchExecutor;
		this.clockMillis = clockMillis;
		this.listener = listener;
		this.candidateBudget = candidateBudget;
		this.hasProfile = library.get(key) != null;
	}

	public synchronized Snapshot getSnapshot() {
		RecognitionRate found = null;
		RecognitionRate defaultRate = null;
		boolean stopped = false;
		if (this.pending != null) {
			found = this.pending.getBestScore().getRate();
			defaultRate = this.pending.getDefaultScore().getRate();
			stopped = this.pending.isStoppedAtLimit();
		}
		return new Snapshot(this.flow.getState(), this.hasProfile, this.heard, this.expected, this.refusalCause, this.quality, this.progressDone, this.progressBudget,
				this.resultKind, found, defaultRate, stopped, this.saveFailed, this.tunerUnavailable, this.diagnosticPath, this.diagnosticFailed);
	}

	/** True while a recording is in memory (being made, or waiting for the quality answer or the analysis). */
	public synchronized boolean isRecordingHeld() {
		return this.segments != null || this.keptSamples != null || (this.recorder != null && (this.recorder.isRecording() || this.recorder.getLength() > 0));
	}

	// ---------------------------------------------------------------- the player's commands

	public synchronized void calibrate() {
		move(Event.CALIBRATE);
	}

	public synchronized void answerTuning(boolean inTune) {
		if (this.flow.getState() != State.TUNING) {
			return;
		}
		if (inTune) {
			move(Event.YES);
			return;
		}
		if (this.tuner.available()) {
			this.tuner.setClosedListener(this::tunerClosed);
			move(Event.NO);
			this.tuner.open();
		} else {
			this.tunerUnavailable = true;
			publish();
		}
	}

	private void tunerClosed() {
		synchronized (this) {
			this.tuner.setClosedListener(null);
			move(Event.TUNER_CLOSED);
		}
	}

	public synchronized void ok() {
		State state = this.flow.getState();
		if (state == State.PIECE) {
			if (move(Event.OK)) {
				startListening();
			}
		} else {
			move(Event.OK);
		}
	}

	/** Where the diagnostic copy of a recording is written when the player ticks the box (FR-020); none by default, and then nothing is ever kept. */
	public synchronized void setDiagnosticSink(DiagnosticSink sink) {
		this.diagnosticSink = sink;
	}

	public synchronized void answerQuality(boolean goodEnough) {
		answerQuality(goodEnough, false);
	}

	/** @param keepCopy the box "Keep a copy of this recording for diagnosis" was ticked: the sink writes the whole recording before it is dropped */
	public synchronized void answerQuality(boolean goodEnough, boolean keepCopy) {
		if (this.flow.getState() != State.QUALITY) {
			return;
		}
		this.diagnosticPath = null;
		this.diagnosticFailed = false;
		if (keepCopy && this.diagnosticSink != null && this.keptSamples != null) {
			this.diagnosticPath = this.diagnosticSink.write(this.keptSamples, this.recordedRate);
			this.diagnosticFailed = this.diagnosticPath == null;
		}
		this.keptSamples = null;
		if (!goodEnough) {
			refuse(RefusalCause.NOT_GOOD_ENOUGH, this.heard, Event.NO);
			return;
		}
		move(Event.YES);
		startSearch();
	}

	public synchronized void recordAgain() {
		if (CalibrationFlow.next(this.flow.getState(), Event.RECORD_AGAIN) == this.flow.getState()) {
			return;
		}
		discardRecording();
		this.diagnosticPath = null;
		this.diagnosticFailed = false;
		this.pending = null;
		this.quality = null;
		this.heard = 0;
		this.refusalCause = null;
		this.resultKind = null;
		move(Event.RECORD_AGAIN);
	}

	/** At a refusal: stop the calibration, nothing saved. */
	public synchronized void stop() {
		if (this.flow.getState() == State.REFUSED) {
			cleanUp();
			move(Event.STOP);
		}
	}

	/** At the offer, a refusal or the choice: the default configuration becomes the profile of the device; nothing is recorded. */
	public synchronized void useDefault() {
		State state = this.flow.getState();
		if (CalibrationFlow.next(state, Event.USE_DEFAULT) == state) {
			return;
		}
		FrontEndState frontEnd = this.recordedFrontEnd != null ? this.recordedFrontEnd : this.port.frontEnd();
		int rate = this.recordedRate > 0 ? this.recordedRate : this.port.sampleRate();
		save(RecognitionProfile.defaultChosen(this.key, rate, this.clockMillis.getAsLong(), frontEnd));
		cleanUp();
		move(Event.USE_DEFAULT);
	}

	/** At the choice: the settings found are kept as the profile although they are not better, or under 60 %. */
	public synchronized void useFound() {
		if (this.flow.getState() != State.CHOICE || this.pending == null) {
			return;
		}
		save(searchedProfile(this.pending));
		cleanUp();
		move(Event.USE_FOUND);
	}

	/** The Cancel button, or the cross answered Yes to "Stop the calibration?". */
	public synchronized void cancel() {
		State before = this.flow.getState();
		if (CalibrationFlow.next(before, Event.CANCEL) == before) {
			return;
		}
		cleanUp();
		move(Event.CANCEL);
	}

	/** The input went away. Up to the start of the analysis that ends the calibration; later the analysis goes on (it uses the recording only). */
	public synchronized void deviceLost() {
		State before = this.flow.getState();
		if (CalibrationFlow.next(before, Event.DEVICE_LOST) == before) {
			return;
		}
		cleanUp();
		move(Event.DEVICE_LOST);
	}

	// ---------------------------------------------------------------- listening

	private void startListening() {
		this.heard = 0;
		this.quality = null;
		this.refusalCause = null;
		this.resultKind = null;
		this.pending = null;
		this.saveFailed = false;
		final SoundCounter sounds = new SoundCounter(this.expected, this.defaults.get());
		final HopRecorder recording = new HopRecorder();
		this.counter = sounds;
		this.recorder = recording;
		this.recordedFrontEnd = this.port.frontEnd();
		sounds.setListener(new SoundCounter.Listener() {
			public void soundHeard(int count) {
				onSound(sounds, count);
			}

			public void ended(SoundCounter.State finalState) {
				onEnded(sounds, recording, finalState);
			}
		});
		recording.begin(this.recordedFrontEnd);
		publish();
		this.port.start(sounds, recording);
	}

	private synchronized void onSound(SoundCounter sounds, int count) {
		if (this.counter == sounds && this.flow.getState() == State.LISTENING) {
			this.heard = count;
			publish();
		}
	}

	private synchronized void onEnded(SoundCounter sounds, HopRecorder recording, SoundCounter.State finalState) {
		if (this.counter != sounds || this.flow.getState() != State.LISTENING) {
			return;
		}
		this.port.stop();
		recording.end();
		this.heard = sounds.getHeard();
		switch (finalState) {
		case SILENCE_TIMEOUT:
			refuse(timeoutOrClipped(recording, RefusalCause.SILENCE_TIMEOUT), this.heard, Event.LISTENING_REFUSED);
			return;
		case TOTAL_TIMEOUT:
			refuse(timeoutOrClipped(recording, RefusalCause.TOTAL_TIMEOUT), this.heard, Event.LISTENING_REFUSED);
			return;
		case COMPLETE:
			break;
		default:
			return;
		}
		float[] samples = recording.getSamples();
		int rate = recording.getSampleRate() > 0 ? recording.getSampleRate() : this.port.sampleRate();
		RecordingQuality measured = RecordingQuality.measure(samples, rate, this.heard, this.expected);
		SoundSegmenter.Refusal refusal = measured.getRefusal();
		if (refusal != null) {
			refuse(cause(refusal), this.heard, Event.LISTENING_REFUSED);
			return;
		}
		SoundSegmenter.Result cut = SoundSegmenter.segment(samples, rate, this.expected);
		if (!cut.isAccepted()) {
			refuse(cause(cut.getRefusal()), cut.getHeard(), Event.LISTENING_REFUSED);
			return;
		}
		this.segments = cut.getSegments();
		this.keptSamples = this.diagnosticSink != null ? samples : null;
		this.recordedRate = rate;
		this.quality = measured;
		recording.discard();
		move(Event.LISTENING_ENDED);
	}

	/**
	 * A take whose sounds could not be counted because it is clipped would end on a timeout, and "nothing heard for 20 seconds" would hide the real
	 * cause: the recording is measured and the player is told it is too loud.
	 */
	private RefusalCause timeoutOrClipped(HopRecorder recording, RefusalCause timeout) {
		int rate = recording.getSampleRate() > 0 ? recording.getSampleRate() : this.port.sampleRate();
		return RecordingQuality.measure(recording.getSamples(), rate, this.heard, this.expected).isClipped() ? RefusalCause.CLIPPED : timeout;
	}

	private static RefusalCause cause(SoundSegmenter.Refusal refusal) {
		switch (refusal) {
		case SILENT:
			return RefusalCause.SILENT;
		case CLIPPED:
			return RefusalCause.CLIPPED;
		default:
			return RefusalCause.WRONG_COUNT;
		}
	}

	private void refuse(RefusalCause cause, int soundsHeard, Event event) {
		discardRecording();
		this.refusalCause = cause;
		this.heard = soundsHeard;
		move(event);
	}

	// ---------------------------------------------------------------- the search

	private void startSearch() {
		final List<float[]> cut = this.segments;
		final int rate = this.recordedRate;
		final RecognitionSettings start = this.defaults.get();
		this.progressDone = 0;
		this.progressBudget = this.candidateBudget;
		final ProfileSearch running = new ProfileSearch(this.scorers.create(cut, rate), this.candidateBudget, ProfileSearch.BUDGET_MILLIS, System::nanoTime);
		this.search = running;
		this.searchExecutor.execute(new Runnable() {
			public void run() {
				ProfileSearch.Result result = running.run(start, (done, budget) -> onProgress(running, done, budget));
				onSearchEnded(running, result);
			}
		});
	}

	private synchronized void onProgress(ProfileSearch running, int done, int budget) {
		if (this.search == running && this.flow.getState() == State.ANALYSING) {
			this.progressDone = done;
			this.progressBudget = budget;
			publish();
		}
	}

	private synchronized void onSearchEnded(ProfileSearch running, ProfileSearch.Result result) {
		if (this.search != running || this.flow.getState() != State.ANALYSING || result.isCancelled()) {
			return;
		}
		this.search = null;
		this.segments = null;
		this.pending = result;
		RecognitionRate found = result.getBestScore().getRate();
		if (result.isBetterThanDefault() && found.meetsThreshold()) {
			this.resultKind = ResultKind.SAVED;
			save(searchedProfile(result));
			move(Event.SEARCH_SAVED);
		} else {
			this.resultKind = result.isBetterThanDefault() ? ResultKind.BETTER_UNDER_60 : ResultKind.NOT_BETTER;
			move(Event.SEARCH_NEEDS_CHOICE);
		}
	}

	private RecognitionProfile searchedProfile(ProfileSearch.Result result) {
		Map<RecognitionParameter.Number, Double> values = new LinkedHashMap<RecognitionParameter.Number, Double>();
		for (RecognitionParameter.Number parameter : ProfileParameters.LIST) {
			values.put(parameter, Double.valueOf(result.getBest().get(parameter)));
		}
		return new RecognitionProfile(this.key, this.recordedRate, this.clockMillis.getAsLong(), values, result.getBestScore().getRate(),
				result.getDefaultScore().getRate(), this.recordedFrontEnd != null ? this.recordedFrontEnd : this.port.frontEnd(), RecognitionProfile.Origin.SEARCHED);
	}

	private void save(RecognitionProfile profile) {
		this.saveFailed = !this.library.save(profile);
	}

	// ---------------------------------------------------------------- housekeeping

	private void discardRecording() {
		this.segments = null;
		this.keptSamples = null;
		if (this.recorder != null) {
			this.recorder.discard();
		}
	}

	/** Stops everything that runs and drops the recording: used when the calibration ends or is cancelled. */
	private void cleanUp() {
		ProfileSearch running = this.search;
		this.search = null;
		if (running != null) {
			running.cancel();
		}
		SoundCounter sounds = this.counter;
		if (sounds != null && sounds.getState() == SoundCounter.State.LISTENING) {
			sounds.cancel();
		}
		this.port.stop();
		this.tuner.setClosedListener(null);
		discardRecording();
	}

	private boolean move(Event event) {
		State before = this.flow.getState();
		State after = this.flow.on(event);
		publish();
		return after != before;
	}

	private void publish() {
		this.listener.changed(getSnapshot());
	}
}
