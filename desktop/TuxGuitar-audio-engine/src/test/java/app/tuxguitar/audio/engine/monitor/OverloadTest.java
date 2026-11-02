package app.tuxguitar.audio.engine.monitor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * The chain made lighter when the computer cannot keep up (spec 022 FR-014, contracts/monitor-engine.md "Overload"; task T049): a fake clock makes every
 * block take 60 % of the block time; after each 20 such blocks one step lighter, with a message, then the sound is muted.
 */
public class OverloadTest {

	private static final int RATE = 48000;
	private static final int BLOCK = 128;

	@Test
	public void everyTwentySlowBlocksTheChainGetsLighterThenTheSoundIsMuted() throws Exception {
		FakeMonitorDevice device = new FakeMonitorDevice();
		device.rate = RATE;
		float[] quiet = new float[20 * RATE];
		for (int i = 0; i < quiet.length; i++) {
			quiet[i] = (float) (0.01 * Math.sin(2 * Math.PI * 220 * i / RATE));
		}
		device.play(quiet);
		long blockNanos = Math.round(BLOCK * 1_000_000_000.0 / RATE);
		AtomicLong now = new AtomicLong();
		// each reading of the clock moves it on by 60 % of a block: a block's processing, read before and after, takes 60 % of the block time
		long step = Math.round(0.6 * blockNanos);
		List<MonitorNotice> notices = new ArrayList<MonitorNotice>();
		List<Integer> at = new ArrayList<Integer>();
		MonitorEngineTest.Recorder recorder = new MonitorEngineTest.Recorder();
		MonitorEngine.Listener counter = new MonitorEngine.Listener() {
			public void stateChanged(MonitorState state, String reason) {
			}

			public synchronized void notice(MonitorNotice notice) {
				notices.add(notice);
				at.add(device.writes());
			}
		};
		MonitorEngine engine = new MonitorEngine(device, BLOCK, () -> now.addAndGet(step), runnable -> {
			Thread thread = new Thread(runnable, "overload-test");
			thread.setDaemon(true);
			thread.start();
		}, Runnable::run);
		engine.addListener(recorder);
		engine.addListener(counter);
		engine.setChain(ReadyMadeChains.CLEAN_ECHO);
		engine.start("in", -1, "");
		long longest = 0;
		float[] block = new float[BLOCK];
		long deadline = System.currentTimeMillis() + 10000;
		while (engine.state() != MonitorState.MUTED_OVERLOAD && System.currentTimeMillis() < deadline) {
			long began = System.nanoTime();
			device.feed(block);
			longest = Math.max(longest, System.nanoTime() - began);
			Thread.sleep(1);
		}
		Assertions.assertTrue(recorder.await(MonitorState.MUTED_OVERLOAD.name(), 1000), recorder.events().toString());
		Assertions.assertEquals(Arrays.asList(MonitorNotice.LIGHTER_REVERB_OFF, MonitorNotice.LIGHTER_CHORUS_OFF, MonitorNotice.LIGHTER_OVERSAMPLING_2X,
				MonitorNotice.LIGHTER_OVERSAMPLING_1X, MonitorNotice.LIGHTER_AMP_ALONE), notices);
		for (int i = 0; i < at.size(); i++) {
			Assertions.assertEquals(20 * (i + 1), at.get(i), 1, "step " + (i + 1) + " after its 20 slow blocks: " + at);
		}
		Assertions.assertTrue(device.writes() <= 20 * 6 + 1 + 0.020 * RATE / BLOCK + 1, "muted after 20 more slow blocks and the fade out: " + device.writes());
		Assertions.assertTrue(longest < 5_000_000L, "the input's hand-over is never slowed: " + longest / 1e6 + " ms");
	}

	@Test
	public void theLightenedChainDropsTheReverbThenTheChorusThenEveryPedal() {
		Chain chain = new Chain();
		chain.prepare(RATE, BLOCK);
		chain.apply(ReadyMadeChains.CLEAN_ECHO);
		Assertions.assertTrue(chain.wanted(EffectKind.REVERB));
		chain.lighten(1);
		Assertions.assertFalse(chain.wanted(EffectKind.REVERB), "the reverb off");
		Assertions.assertTrue(chain.wanted(EffectKind.CHORUS));
		chain.lighten(2);
		Assertions.assertFalse(chain.wanted(EffectKind.CHORUS), "the chorus off");
		Assertions.assertEquals(4, chain.oversampling());
		chain.lighten(3);
		Assertions.assertEquals(2, chain.oversampling());
		chain.lighten(4);
		Assertions.assertEquals(1, chain.oversampling());
		Assertions.assertTrue(chain.wanted(EffectKind.ECHO));
		chain.lighten(5);
		for (EffectKind kind : EffectKind.values()) {
			Assertions.assertFalse(chain.wanted(kind), kind + " bypassed: the amp and cabinet alone");
		}
		chain.apply(ReadyMadeChains.LEAD);
		Assertions.assertFalse(chain.wanted(EffectKind.COMPRESSOR), "new settings stay lightened");
	}
}
