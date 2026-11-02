package app.tuxguitar.audio.engine.monitor;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.benchmark.MonitorFixtureSet;
import app.tuxguitar.audio.engine.monitor.fx.OutputStage;

/**
 * The feedback guard (spec 022 FR-012, SC-005, research R5; task T048): a howl is muted within half a second, the ready-made sounds played on
 * the recorded guitar never trigger it, and turning the sound on again restarts it.
 */
public class FeedbackGuardTest {

	private static final int RATE = 48000;
	private static final int BLOCK = 128;

	private static double db(double level) {
		return Math.pow(10, level / 20);
	}

	/** Guitar-like noise: white noise through a gentle low-pass, at about {@code level} dBFS. */
	private static float[] noise(int frames, double level, long seed) {
		Random random = new Random(seed);
		float[] samples = new float[frames];
		double state = 0;
		for (int i = 0; i < frames; i++) {
			state += 0.2 * (random.nextGaussian() - state);
			samples[i] = (float) state;
		}
		double sum = 0;
		for (float sample : samples) {
			sum += (double) sample * sample;
		}
		double scale = db(level) / Math.sqrt(sum / frames);
		for (int i = 0; i < frames; i++) {
			samples[i] *= scale;
		}
		return samples;
	}

	/** A 2.3 kHz sine starting at {@code start} at −50 dBFS and rising 6 dB per 100 ms, held at −3 dBFS once there, over the noise. */
	private static float[] howl(int frames, int start) {
		float[] samples = noise(frames, -50, 3);
		for (int i = start; i < frames; i++) {
			double seconds = (i - start) / (double) RATE;
			double level = Math.min(-3, -50 + 60 * seconds);
			samples[i] += (float) (db(level) * Math.sin(2 * Math.PI * 2300 * seconds));
		}
		return samples;
	}

	private static MonitorEngine engine(FakeMonitorDevice device, MonitorEngineTest.Recorder recorder) {
		MonitorEngine engine = new MonitorEngine(device, BLOCK, System::nanoTime, runnable -> {
			Thread thread = new Thread(runnable, "feedback-test");
			thread.setDaemon(true);
			thread.start();
		}, Runnable::run);
		engine.addListener(recorder);
		return engine;
	}

	@Test
	public void aHowlIsMutedWithinHalfASecond() throws Exception {
		FakeMonitorDevice device = new FakeMonitorDevice();
		device.rate = RATE;
		int start = RATE / 2;
		device.play(howl(4 * RATE, start));
		MonitorEngineTest.Recorder recorder = new MonitorEngineTest.Recorder();
		MonitorEngine engine = engine(device, recorder);
		engine.setChain(null);
		engine.setGuitarVolume(1);
		engine.start("in", -1, "");
		Assertions.assertTrue(recorder.await(MonitorState.MUTED_FEEDBACK.name(), 10000), recorder.events().toString());
		Assertions.assertEquals(MonitorState.MUTED_FEEDBACK, engine.state());
		Assertions.assertEquals(1, engine.stats().howls());
		float[] out = device.allLeft();
		int limit = start + RATE / 2;
		Assertions.assertTrue(out.length > start, "the sound played before the howl");
		for (int i = limit; i < out.length; i++) {
			Assertions.assertEquals(0f, out[i], 1e-4f, "silent half a second after the howl began, at " + i);
		}
	}

	@Test
	public void theReadyMadeSoundsOnTheRecordedGuitarNeverTriggerIt() throws Exception {
		List<MonitorFixtureSet.Input> inputs = MonitorFixtureSet.recognitionInputs();
		Assertions.assertFalse(inputs.isEmpty());
		for (ReadyMadeChains.Named named : ReadyMadeChains.all()) {
			for (MonitorFixtureSet.Input input : inputs) {
				Assertions.assertFalse(howls(named.chain(), input.samples()), named.name() + " on " + input.name());
			}
			Assertions.assertFalse(howls(named.chain(), sustained(4 * RATE, 196, -3)), named.name() + " on a long low note");
			Assertions.assertFalse(howls(named.chain(), sustained(4 * RATE, 659.3, -3)), named.name() + " on a long high note");
		}
	}

	/** A plucked note held for its whole length: ten harmonics, a slow decay (6 dB per second), peaking at {@code level} dBFS, then the tails. */
	private static float[] sustained(int frames, double frequency, double level) {
		float[] samples = new float[frames + 2 * RATE];
		for (int i = 0; i < frames; i++) {
			double seconds = i / (double) RATE;
			double value = 0;
			for (int k = 1; k <= 10; k++) {
				value += Math.sin(2 * Math.PI * frequency * k * seconds) / k;
			}
			samples[i] = (float) (value / 3 * db(level - 6 * seconds) * Math.min(1, seconds * 200));
		}
		return samples;
	}

	/** The chain, the output stage at its default volume and the guard on the processed output once faded in, block by block, as the engine does. */
	private static boolean howls(ChainSettings settings, float[] samples) {
		Chain chain = new Chain();
		chain.prepare(RATE, BLOCK);
		chain.apply(settings);
		OutputStage stage = new OutputStage(RATE, BLOCK);
		stage.setVolume(0.7);
		stage.start();
		FeedbackGuard guard = new FeedbackGuard(RATE, BLOCK);
		float[] block = new float[BLOCK];
		for (int offset = 0; offset + BLOCK <= samples.length; offset += BLOCK) {
			System.arraycopy(samples, offset, block, 0, BLOCK);
			chain.process(block, BLOCK);
			stage.process(block, BLOCK);
			if (stage.isFadedIn() && guard.process(block, BLOCK)) {
				return true;
			}
		}
		return false;
	}

	@Test
	public void turningTheSoundOnAgainRestartsIt() throws Exception {
		FakeMonitorDevice device = new FakeMonitorDevice();
		device.rate = RATE;
		device.play(howl(3 * RATE, RATE / 4));
		MonitorEngineTest.Recorder recorder = new MonitorEngineTest.Recorder();
		MonitorEngine engine = engine(device, recorder);
		engine.setChain(null);
		engine.start("in", -1, "");
		Assertions.assertTrue(recorder.await(MonitorState.MUTED_FEEDBACK.name(), 10000), recorder.events().toString());
		device.play(noise(2 * RATE, -40, 9));
		int before = recorder.events().size();
		engine.start("in", -1, "");
		long deadline = System.currentTimeMillis() + 10000;
		while (engine.state() != MonitorState.ON && System.currentTimeMillis() < deadline) {
			Thread.sleep(10);
		}
		Assertions.assertEquals(MonitorState.ON, engine.state(), recorder.events().toString());
		Assertions.assertEquals(MonitorState.STARTING.name(), recorder.events().get(before));
		Assertions.assertEquals(2, device.opens(), "the device opened again");
		engine.stop();
	}
}
