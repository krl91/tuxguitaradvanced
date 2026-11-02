package app.tuxguitar.audio.engine.monitor;

import java.lang.management.ManagementFactory;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * The chain allocates nothing while it runs (constitution VI, spec 022 research R3): the full chain of T034 over 20 s, settings changed every second,
 * measured with the JVM's count of the bytes this thread allocated, after a warm-up that lets the compiler settle.
 */
public class ChainAllocationTest {

	@Test
	public void theFullChainAllocatesNothingPerBlock() {
		com.sun.management.ThreadMXBean threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
		int rate = 48000;
		int block = MonitorEngine.BLOCK_FRAMES;
		Chain chain = new Chain();
		chain.prepare(rate, block);
		ChainSettings heavy = ChainCostBenchmarkTest.heaviest();
		ChainSettings light = ReadyMadeChains.CLEAN_ECHO;
		float[] samples = new float[block];
		long measured = 0;
		for( int pass = 0 ; pass < 2 ; pass++ ) {
			long before = threads.getThreadAllocatedBytes(Thread.currentThread().getId());
			for( int b = 0 ; b < 20 * rate / block ; b++ ) {
				if( b % (rate / block) == 0 ) {
					chain.apply((b / (rate / block)) % 2 == 0 ? heavy : light);
				}
				for( int i = 0 ; i < block ; i++ ) {
					samples[i] = (float) (0.3 * Math.sin(2 * Math.PI * 196 * (b * block + i) / rate));
				}
				chain.process(samples, block);
			}
			measured = threads.getThreadAllocatedBytes(Thread.currentThread().getId()) - before;
		}
		Assertions.assertTrue(measured < 64 * 1024, "the chain allocated " + measured + " bytes in 20 s");
	}
}
