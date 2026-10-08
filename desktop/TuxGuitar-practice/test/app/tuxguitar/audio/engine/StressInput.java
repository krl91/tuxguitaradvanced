package app.tuxguitar.audio.engine;

import app.tuxguitar.audio.engine.input.CaptureClock;
import app.tuxguitar.audio.engine.input.CurrentInputSelection;
import app.tuxguitar.audio.engine.input.InputDeviceCatalog;
import app.tuxguitar.audio.engine.input.SampleRingBuffer;
import app.tuxguitar.audio.engine.settings.PitchEngineCatalog;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.util.TGContext;

/**
 * The live audio chain without the sound card (spec 003 T047): the real {@link AudioEngine} (ring buffer, analysis thread, front end, frames, capture
 * clock) whose input is written in real time by a feeder instead of read from Java Sound. It lives in the engine's package because the engine's test
 * constructor and ring buffer are package-private, like {@code FakeAudioInput} of the engine's own tests: the public API is not widened.
 */
public final class StressInput {

	public static final int SAMPLE_RATE = 48000;

	private final AudioEngine engine;

	private StressInput(AudioEngine engine) {
		this.engine = engine;
		engine.getCaptureClock().configure(SAMPLE_RATE);
	}

	public static StressInput create(TGContext context, RecognitionSettings settings) {
		AudioEngine engine = new AudioEngine(context, new InputDeviceCatalog(), CurrentInputSelection.getInstance(context), AudioEngine.DEFAULT_RING_BUFFER_CAPACITY,
				AudioEngine.levelAnalyzer(settings), AudioEngine.articulationDetector(settings),
				sampleRate -> PitchEngineCatalog.resolve(settings.get(RecognitionParameters.ENGINE_PITCH)).create(sampleRate), null);
		return new StressInput(engine);
	}

	public AudioEngine engine() {
		return this.engine;
	}

	/** Writes a block as the capture would, then tells the capture clock when it arrived. Returns the number of samples the ring buffer accepted. */
	public int write(float[] block, int length) {
		SampleRingBuffer buffer = this.engine.getRingBuffer();
		int accepted = buffer.write(block, 0, length);
		CaptureClock clock = this.engine.getCaptureClock();
		clock.onSamples(System.nanoTime(), buffer.getWrittenSampleCount());
		return accepted;
	}

	/** The samples the ring buffer refused because the analysis did not keep up: a capture overrun. */
	public long droppedSamples() {
		return this.engine.getDroppedSampleCount();
	}
}
