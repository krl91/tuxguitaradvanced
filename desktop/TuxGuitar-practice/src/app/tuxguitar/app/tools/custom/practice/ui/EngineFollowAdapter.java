package app.tuxguitar.app.tools.custom.practice.ui;

import app.tuxguitar.app.tools.custom.practice.session.AudioRoute;
import app.tuxguitar.app.tools.custom.practice.session.CalibrationRun;
import app.tuxguitar.app.tools.custom.practice.session.FollowPlayer;
import app.tuxguitar.app.tools.custom.practice.session.FollowStarter;
import app.tuxguitar.app.tools.custom.practice.session.MidiClickPlayer;
import app.tuxguitar.app.tools.custom.practice.session.RemovedSoundsPlayer;
import app.tuxguitar.app.tools.custom.practice.session.MidiFollowPlayer;
import app.tuxguitar.app.tools.custom.practice.session.PracticeParameters;
import app.tuxguitar.app.tools.custom.practice.session.RouteCalibrationStore;
import app.tuxguitar.app.tools.custom.practice.session.RouteCalibrations;
import app.tuxguitar.audio.engine.AudioEngine;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.analysis.AnalysisService;
import app.tuxguitar.audio.engine.input.CaptureClock;
import app.tuxguitar.audio.engine.input.CurrentInputSelection;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.audio.engine.settings.RecognitionSettingsStore;
import app.tuxguitar.player.base.MidiOutputPort;
import app.tuxguitar.player.base.MidiPlayer;
import app.tuxguitar.player.base.MidiPlayerException;
import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.TGSynchronizer;

import javax.sound.sampled.AudioFormat;

/**
 * The application behind the tempo-following mode (spec 002 T096, T097, T098): the audio engine and its capture clock, the player, the route of the
 * audio and its calibrations. A run takes everything it needs from here, and the calibration of the panel plays its clicks through the same player.
 * <p>
 * The route is the input and the format that were negotiated, and the output port of the player: parts of the route are never read from the name or
 * the transport of a device beyond being kept as its identity (FR-057).
 */
public final class EngineFollowAdapter implements PracticeHost.RunEnvironment {

	private final TGContext context;
	private final AudioEngine engine;
	private final MidiPlayer midiPlayer;
	private final MidiFollowPlayer player;
	private final RouteCalibrations calibrations;
	private AnalysisService.Listener calibrationListener;

	public EngineFollowAdapter(TGContext context, AudioEngine engine) {
		this.context = context;
		this.engine = engine;
		this.midiPlayer = MidiPlayer.getInstance(context);
		this.player = new MidiFollowPlayer(this.midiPlayer);
		this.calibrations = RouteCalibrationStore.getInstance(context);
	}

	// ------------------------------------------------------------------------------------------------------------- Environment

	@Override
	public FollowPlayer player() {
		return this.player;
	}

	@Override
	public long wallNanosOf(AnalysisFrame frame) {
		return this.engine.wallNanosOf(frame);
	}

	@Override
	public boolean captureReady() {
		return this.engine.getCaptureClock().isReady();
	}

	@Override
	public double lagSpreadMillis() {
		CaptureClock clock = this.engine.getCaptureClock();
		return clock.isReady() ? clock.lagSpreadMillis() : Double.NaN;
	}

	@Override
	public AudioRoute route() {
		CurrentInputSelection.Snapshot snapshot = this.engine.getSelection().snapshot();
		// the selection holds no format (an input is selected alone): the one the capture negotiated for that input
		AudioFormat format = (snapshot.getFormat() != null ? snapshot.getFormat() : this.engine.getNegotiatedFormat(snapshot.getInput()));
		if (snapshot.getInput() == null || format == null) {
			return null;
		}
		MidiOutputPort output = this.midiPlayer.getOutputPort();
		String outputKey = (output != null ? output.getKey() + " " + output.getName() : "none");
		String profile = format.getChannels() + "ch " + format.getSampleSizeInBits() + "bit";
		return new AudioRoute(snapshot.getInput().getId(), outputKey, Math.round(format.getSampleRate()), profile);
	}

	@Override
	public RouteCalibrations calibrations() {
		return this.calibrations;
	}

	@Override
	public InputAssessment assessment() {
		return this.engine.getSelection().snapshot().getAssessment();
	}

	// ------------------------------------------------------------------------------------------------------------- Backend of the panel

	@Override
	public CalibrationRun newCalibration() {
		InputAssessment assessment = assessment();
		if (route() == null || assessment.isUnusable() || playerBusy()) {
			return null;
		}
		PracticeParameters parameters = PracticeParameters.of(RecognitionSettingsStore.getInstance(this.context).get());
		return new CalibrationRun(new MidiClickPlayer(this.midiPlayer), parameters.articulationLatencyNanos());
	}

	/** The output of the player, as the editor audition and the calibration click use it: a note on the channel of its track, straight to the transmitter. */
	@Override
	public RemovedSoundsPlayer.Output removedSoundsOutput() {
		final boolean[] prepared = { false };
		return new RemovedSoundsPlayer.Output() {
			public void noteOn(int channel, int key, int velocity) {
				try {
					if (!prepared[0]) {
						EngineFollowAdapter.this.midiPlayer.updateChannels();
						prepared[0] = true;
					}
					EngineFollowAdapter.this.midiPlayer.getOutputTransmitter().sendNoteOn(channel, key, velocity, -1, false);
				} catch (MidiPlayerException | RuntimeException exception) {
					// a note that cannot be sent is a note not heard: the run goes on
				}
			}

			public void noteOff(int channel, int key) {
				try {
					EngineFollowAdapter.this.midiPlayer.getOutputTransmitter().sendNoteOff(channel, key, 0, -1, false);
				} catch (MidiPlayerException | RuntimeException exception) {
					// the same
				}
			}
		};
	}

	@Override
	public boolean playerBusy() {
		return this.midiPlayer.isRunning();
	}

	@Override
	public synchronized void begin(CalibrationRun run, Runnable changed) {
		end(run);
		final int[] seen = { -1, -1 };
		this.calibrationListener = frame -> {
			run.onFrame(frame, this.engine.wallNanosOf(frame));
			// the window is told when something it shows moved, not at every frame
			int played = run.clicksPlayed();
			int state = run.state().ordinal();
			if (played != seen[0] || state != seen[1]) {
				seen[0] = played;
				seen[1] = state;
				TGSynchronizer.getInstance(this.context).executeLater(changed);
			}
		};
		this.engine.subscribe(this.calibrationListener);
	}

	@Override
	public synchronized void end(CalibrationRun run) {
		if (this.calibrationListener != null) {
			this.engine.unsubscribe(this.calibrationListener);
			this.calibrationListener = null;
		}
	}
}
